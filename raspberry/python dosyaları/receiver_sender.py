import pyaudio
import socket
import threading
import time

RECEIVE_IP = "0.0.0.0"
RECEIVE_PORT = 8555

TARGET_IP = "192.168.1.128"
TARGET_PORT = 8554

SAMPLE_RATE = 44100
CHANNELS = 1
FORMAT = pyaudio.paInt16
CHUNK = 1024


audio = pyaudio.PyAudio()


def receiver():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((RECEIVE_IP, RECEIVE_PORT))

    stream_out = audio.open(format=FORMAT, channels=CHANNELS,
                            rate=SAMPLE_RATE, output=True,
                            frames_per_buffer=CHUNK)

    print(f"[RECEIVER] Dinleniyor: {RECEIVE_IP}:{RECEIVE_PORT}")

    while True:
        data, addr = sock.recvfrom(8192)
        stream_out.write(data)


def sender():
    time.sleep(2)

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    server_addr = (TARGET_IP, TARGET_PORT)

    stream_in = audio.open(format=FORMAT, channels=CHANNELS,
                           rate=SAMPLE_RATE, input=True,
                           frames_per_buffer=CHUNK)

    print(f"[SENDER] Gönderiyor: {TARGET_IP}:{TARGET_PORT}")

    while True:
        data = stream_in.read(CHUNK, exception_on_overflow=False)
        sock.sendto(data, server_addr)


if __name__ == "__main__":
    t1 = threading.Thread(target=receiver, daemon=True)
    t2 = threading.Thread(target=sender, daemon=True)

    t1.start()
    t2.start()

    print("SENDER ve RECEIVER aynı anda çalışıyor...")

    while True:
        time.sleep(1)
