#!/usr/bin/env python3
import gi
gi.require_version("Gst", "1.0")
gi.require_version("GstRtspServer", "1.0")
from gi.repository import Gst, GstRtspServer, GObject

Gst.init(None)

class RTSPServer(GstRtspServer.RTSPServer):
    def __init__(self):
        super(RTSPServer, self).__init__()
        factory = GstRtspServer.RTSPMediaFactory()
        factory.set_launch(
        "( v4l2src device=/dev/video0 ! "
        "video/x-raw,framerate=30/1,width=640,height=480 ! "
        "videoconvert ! x264enc tune=zerolatency speed-preset=ultrafast bitrate=1500 ! "
        "rtph264pay name=pay0 pt=96 )"
        )
        factory.set_shared(True)
        self.get_mount_points().add_factory("/camera", factory)

server = RTSPServer()
server.attach(None)

loop = GObject.MainLoop()
loop.run()
