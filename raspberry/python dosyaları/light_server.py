#!/usr/bin/env python3
"""
Raspberry Pi 5 - Işık Kontrol Sunucusu
TCP Port: 8556
Protokol: {"light":"front","state":"on"} + \n
"""
import os
os.environ["GPIOZERO_PIN_FACTORY"] = "lgpio"
import socket
import json
import threading
import time
from gpiozero import LED

# ── Ayarlar ──────────────────────────────────────────────
LIGHT_PIN = 17        # GPIO 18 → fiziksel pin 12 (gerçek pinini yaz)
HOST      = "0.0.0.0"
PORT      = 8556
# ─────────────────────────────────────────────────────────

light = LED(LIGHT_PIN)

# Selektör durumu — thread-safe
selector_lock   = threading.Lock()
selector_active = False
selector_thread = None


# ── Selektör yanıp-sönme döngüsü ─────────────────────────
def selector_blink_loop():
    """1 saniyede bir aç/kapa — selector_active False olunca durur."""
    global selector_active
    while True:
        with selector_lock:
            if not selector_active:
                light.off()
                break
        light.on()
        time.sleep(0.25)

        with selector_lock:
            if not selector_active:
                light.off()
                break
        light.off()
        time.sleep(0.25)


def stop_selector():
    """Varsa çalışan selektör thread'ini durdurur."""
    global selector_active, selector_thread
    with selector_lock:
        selector_active = False
    if selector_thread and selector_thread.is_alive():
        selector_thread.join(timeout=3.0)


def start_selector():
    """Yeni selektör thread'i başlatır."""
    global selector_active, selector_thread
    stop_selector()                        # önce eskiyi bitir
    with selector_lock:
        selector_active = True
    selector_thread = threading.Thread(
        target=selector_blink_loop,
        daemon=True,
        name="selector-blink"
    )
    selector_thread.start()


# ── JSON komutunu işle ────────────────────────────────────
def handle_command(raw: str):
    try:
        cmd        = json.loads(raw)
        light_type = cmd.get("light", "")
        state      = cmd.get("state", "")
        print(f"[CMD] light={light_type}  state={state}")

        if light_type == "front":
            stop_selector()            # selektör çalışıyorsa durdur
            if state == "on":
                light.on()
                print("[IŞIK] Sabit açık")
            else:
                light.off()
                print("[IŞIK] Kapalı")

        elif light_type == "selector":
            if state == "on":
                start_selector()
                print("[IŞIK] Selektör başladı (1sn aralıklı)")
            else:
                stop_selector()
                light.off()
                print("[IŞIK] Selektör durdu")

        else:
            print(f"[UYARI] Bilinmeyen light tipi: {light_type}")

    except json.JSONDecodeError as e:
        print(f"[HATA] JSON parse: {e} | Ham veri: {raw!r}")


# ── İstemci bağlantısını ele al ──────────────────────────
def handle_client(conn, addr):
    print(f"[BAĞLANTI] {addr}")
    try:
        buffer = b""
        while True:
            chunk = conn.recv(1024)
            if not chunk:
                break
            buffer += chunk
            if b"\n" in buffer:
                line, buffer = buffer.split(b"\n", 1)
                raw = line.decode("utf-8").strip()
                if raw:
                    handle_command(raw)
                break           # fire-and-forget: Android her komut için yeni bağlantı açıyor
    except Exception as e:
        print(f"[HATA] İstemci {addr}: {e}")
    finally:
        conn.close()


# ── Ana sunucu döngüsü ────────────────────────────────────
def main():
    print(f"Raspberry Pi Işık Sunucusu başlıyor → {HOST}:{PORT}")
    print(f"GPIO pin: {LIGHT_PIN}")

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((HOST, PORT))
        server.listen(20)
        print("Bekleniyor...\n")

        while True:
            conn, addr = server.accept()
            # Her bağlantıyı ayrı thread'de işle
            t = threading.Thread(
                target=handle_client,
                args=(conn, addr),
                daemon=True
            )
            t.start()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nSunucu kapatılıyor...")
        stop_selector()
        light.off()
