package me.ferlo.netty.core.cpacket;

import me.ferlo.netty.CustomByteBuf;

public class EnableUdpPacket implements CPacket {

    private final String udpIp;
    private final int udpPort;

    public EnableUdpPacket(String udpIp,
                           int udpPort) {

        // Max IP length taken from here: https://stackoverflow.com/a/7477384
        if(udpIp.length() > 45)
            udpIp = udpIp.substring(0, 45);

        this.udpIp = udpIp;
        this.udpPort = udpPort;
    }

    public String getUdpIp() {
        return udpIp;
    }

    public int getUdpPort() {
        return udpPort;
    }

    @Override
    public String toString() {
        return "EnableUdpPacket{" +
                "udpIp='" + udpIp + '\'' +
                ", udpPort=" + udpPort +
                '}';
    }

    @Override
    public void writePacket(CustomByteBuf buf) {
        buf.writeString(udpIp);
        buf.writeInt(udpPort);
    }

    public static final CPacketParser PARSER = (CustomByteBuf buf) -> {

        final String host = buf.readString(45);
        final int port = buf.readInt();

        return new EnableUdpPacket(host, port);
    };
}
