#!/usr/bin/env python3
import json, socket, signal, sys, time, threading, struct, serial
import lgpio

UDP_IP         = "0.0.0.0"
UDP_PORT       = 5005
PIN_RPWM       = 18
PIN_LPWM       = 19
PWM_FREQ       = 1000
PIN_SERVO      = 12
SERVO_FREQ     = 50
SERVO_MIN_US   = 2000
SERVO_MID_US   = 1500
SERVO_MAX_US   = 1000
PACKET_TIMEOUT = 2.0
MAX_PACKET_AGE = 1.0
LIDAR_PORT     = '/dev/ttyAMA0'
LIDAR_BAUD     = 115200
OBSTACLE_MIN   = 8
OBSTACLE_MAX   = 100
TABLET_IP      = "192.168.1.128"
TABLET_PORT    = 5000

obstacle_lock = threading.Event()

h = lgpio.gpiochip_open(0)
lgpio.gpio_claim_output(h, PIN_RPWM, 0)
lgpio.gpio_claim_output(h, PIN_LPWM, 0)
lgpio.tx_pwm(h, PIN_RPWM, PWM_FREQ, 0)
lgpio.tx_pwm(h, PIN_LPWM, PWM_FREQ, 0)
lgpio.gpio_claim_output(h, PIN_SERVO, 0)

def us_to_duty(us):
    return (us / 20000.0) * 100.0

lgpio.tx_pwm(h, PIN_SERVO, SERVO_FREQ, us_to_duty(SERVO_MID_US))

def motor_stop():
    lgpio.tx_pwm(h, PIN_RPWM, PWM_FREQ, 0)
    lgpio.tx_pwm(h, PIN_LPWM, PWM_FREQ, 0)

def motor_ileri(duty):
    lgpio.tx_pwm(h, PIN_LPWM, PWM_FREQ, 0)
    lgpio.tx_pwm(h, PIN_RPWM, PWM_FREQ, duty)

def motor_geri(duty):
    lgpio.tx_pwm(h, PIN_RPWM, PWM_FREQ, 0)
    lgpio.tx_pwm(h, PIN_LPWM, PWM_FREQ, duty)

def apply_x(x):
    x = max(-1.0, min(1.0, x))
    if x >= 0:
        pulse_us = SERVO_MID_US + x * (SERVO_MAX_US - SERVO_MID_US)
    else:
        pulse_us = SERVO_MID_US + x * (SERVO_MID_US - SERVO_MIN_US)
    lgpio.tx_pwm(h, PIN_SERVO, SERVO_FREQ, us_to_duty(pulse_us))

def apply(y, x):
    """
    Tek karar noktası.
    Engel varsa YALNIZCA ileri (y < 0) bloklanır.
    Geri, sağ, sol her zaman çalışır.
    """
    # Direksiyon her zaman uygulanır
    apply_x(x)

    # Engel aktif + ileri komut → sadece motoru durdur
    if obstacle_lock.is_set() and y < 0:
        motor_stop()
        return

    # Diğer tüm durumlar normal çalışır
    if abs(y) < 0.01:
        motor_stop()
    elif y < 0:
        motor_ileri(abs(y) * 100.0)
    else:
        motor_geri(y * 100.0)

def shutdown(sig=None, frame=None):
    print("\n[motor] Kapatiliyor.")
    motor_stop()
    lgpio.tx_pwm(h, PIN_SERVO, SERVO_FREQ, us_to_duty(SERVO_MID_US))
    time.sleep(0.2)
    lgpio.tx_pwm(h, PIN_RPWM, PWM_FREQ, 0)
    lgpio.tx_pwm(h, PIN_LPWM, PWM_FREQ, 0)
    lgpio.tx_pwm(h, PIN_SERVO, SERVO_FREQ, 0)
    time.sleep(0.1)
    lgpio.gpiochip_close(h)
    sys.exit(0)

signal.signal(signal.SIGINT,  shutdown)
signal.signal(signal.SIGTERM, shutdown)

def lidar_thread():
    tablet_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    last_alert  = 0
    prev_locked = False

    try:
        ser = serial.Serial(LIDAR_PORT, LIDAR_BAUD, timeout=1)
        print(f"[lidar] TFmini bagli: {LIDAR_PORT}")
    except Exception as e:
        print(f"[lidar] HATA: {e}")
        return

    while True:
        try:
            if ser.read() != b'Y':
                continue
            if ser.read() != b'Y':
                continue

            frame    = ser.read(7)
            distance = struct.unpack('<H', frame[0:2])[0]
            print(f"[lidar] {distance} cm")

            if OBSTACLE_MIN <= distance <= OBSTACLE_MAX:
                obstacle_lock.set()
                if not prev_locked:
                    print(f"[lidar] ENGEL {distance}cm — ileri hareket bloke")
                    prev_locked = True
                now = time.time()
                if now - last_alert > 0.3:
                    tablet_sock.sendto(
                        f"OBSTACLE {distance}cm".encode(),
                        (TABLET_IP, TABLET_PORT)
                    )
                    last_alert = now
            else:
                obstacle_lock.clear()
                if prev_locked:
                    print(f"[lidar] Temiz ({distance}cm) — tum hareketler serbest")
                    prev_locked = False

        except Exception as e:
            print(f"[lidar] Hata: {e}")
            time.sleep(0.1)

def main():
    threading.Thread(target=lidar_thread, daemon=True).start()

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((UDP_IP, UDP_PORT))
    sock.settimeout(PACKET_TIMEOUT)

    print(f"[motor] UDP dinleniyor -> {UDP_IP}:{UDP_PORT}")

    packet_count     = 0
    last_packet_time = time.time()
    watchdog_printed = False

    while True:
        try:
            data, addr = sock.recvfrom(256)
            now = time.time()

            try:
                msg = json.loads(data.decode("utf-8"))
            except Exception:
                continue

            ts = msg.get("ts", None)
            if ts is not None:
                if now - (ts / 1000.0) > MAX_PACKET_AGE:
                    continue

            y = max(-1.0, min(1.0, float(msg.get("y", 0.0))))
            x = max(-1.0, min(1.0, float(msg.get("x", 0.0))))

            # Tüm kararlar apply() içinde — başka hiçbir yerde kilit kontrolü yok
            apply(y, x)

            last_packet_time = now
            packet_count    += 1
            watchdog_printed = False

            if packet_count % 20 == 0:
                engel  = " [ENGEL-ileri bloke]" if obstacle_lock.is_set() else ""
                yon    = "ILERI" if y < -0.01 else ("GERI" if y > 0.01 else "DUR  ")
                direk  = "SOLA " if x < -0.01 else ("SAGA " if x > 0.01 else "DUZ  ")
                print(f"[motor] #{packet_count:5d} | y={y:+.3f} {yon} | "
                      f"x={x:+.3f} {direk}{engel}")

        except socket.timeout:
            motor_stop()
            if not watchdog_printed:
                print("[motor] Sinyal yok — motor durduruldu.")
                watchdog_printed = True

        except Exception as e:
            print(f"[motor] Hata: {e}")
            motor_stop()

if __name__ == "__main__":
    main()
