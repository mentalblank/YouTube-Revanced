package app.revanced.integrations.youtube.vot.proto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Manual protobuf serialization for Yandex Video Translation API.
 * Field numbers match the official VOT protocol (@vot.js/shared/protos/yandex).
 *
 * Protobuf wire format:
 *   - Varint: type 0 (int32, bool)
 *   - 64-bit: type 1 (double)
 *   - Length-delimited: type 2 (string, bytes, embedded messages)
 */
public class TranslationProto {

    // ===================== Wire Format Helpers =====================

    /** Write a varint to the output stream. */
    static void writeVarint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

    /** Write a field tag (field number + wire type). */
    static void writeTag(ByteArrayOutputStream out, int fieldNumber, int wireType) {
        writeVarint(out, ((long) fieldNumber << 3) | wireType);
    }

    /** Write a string field. */
    static void writeString(ByteArrayOutputStream out, int fieldNumber, String value) {
        if (value == null || value.isEmpty()) return;
        writeTag(out, fieldNumber, 2); // length-delimited
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarint(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    /** Write a bool field. */
    static void writeBool(ByteArrayOutputStream out, int fieldNumber, boolean value) {
        if (!value) return;
        writeTag(out, fieldNumber, 0); // varint
        out.write(1);
    }

    /** Write an int32 field. */
    static void writeInt32(ByteArrayOutputStream out, int fieldNumber, int value) {
        if (value == 0) return;
        writeTag(out, fieldNumber, 0); // varint
        writeVarint(out, value);
    }

    /** Write a double field. */
    static void writeDouble(ByteArrayOutputStream out, int fieldNumber, double value) {
        if (value == 0.0) return;
        writeTag(out, fieldNumber, 1); // 64-bit
        byte[] buf = new byte[8];
        ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).putDouble(value);
        out.write(buf, 0, 8);
    }

    // ===================== Reader =====================

    /**
     * Simple protobuf reader for decoding wire-format bytes.
     */
    public static class ProtoReader {
        private final byte[] data;
        private int pos;

        public ProtoReader(byte[] data) {
            this.data = data;
            this.pos = 0;
        }

        public boolean hasRemaining() {
            return pos < data.length;
        }

        public boolean hasRemaining(int end) {
            return pos < end;
        }

        public long readVarint() {
            long result = 0;
            int shift = 0;
            while (pos < data.length) {
                byte b = data[pos++];
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return result;
                shift += 7;
            }
            return result;
        }

        public int readTag() {
            if (!hasRemaining()) return 0;
            return (int) readVarint();
        }

        public String readString() {
            int len = (int) readVarint();
            String s = new String(data, pos, len, StandardCharsets.UTF_8);
            pos += len;
            return s;
        }

        public double readDouble() {
            double v = ByteBuffer.wrap(data, pos, 8).order(ByteOrder.LITTLE_ENDIAN).getDouble();
            pos += 8;
            return v;
        }

        public boolean readBool() {
            return readVarint() != 0;
        }

        public int readInt32() {
            return (int) readVarint();
        }

        public byte[] readBytes() {
            int len = (int) readVarint();
            byte[] result = new byte[len];
            System.arraycopy(data, pos, result, 0, len);
            pos += len;
            return result;
        }

        /** Skip a field based on wire type. */
        public void skipField(int wireType) {
            switch (wireType) {
                case 0: readVarint(); break;          // varint
                case 1: pos += 8; break;              // 64-bit
                case 2: pos += (int) readVarint(); break; // length-delimited
                case 5: pos += 4; break;              // 32-bit
                default: break;
            }
        }

        public int getPos() { return pos; }
    }

    // ===================== Request =====================

    /**
     * VideoTranslationRequest - request to translate a video.
     * Field numbers from @vot.js protocol:
     *   3=url, 4=deviceId, 5=firstRequest, 6=duration(double),
     *   7=unknown0, 8=language, 9=forceSourceLang, 10=unknown1,
     *   11=translationHelp(embedded), 13=wasStream, 14=responseLanguage,
     *   15=unknown2, 16=unknown3, 17=bypassCache, 18=useLivelyVoice, 19=videoTitle
     */
    public static class TranslationRequest {
        public String url = "";
        public String deviceId;
        public boolean firstRequest = false;
        public double duration = 0;
        public int unknown0 = 0;
        public String language = "";         // source language
        public boolean forceSourceLang = false;
        public int unknown1 = 0;
        // translationHelp omitted for simplicity (not needed for basic requests)
        public boolean wasStream = false;
        public String responseLanguage = ""; // target language
        public int unknown2 = 0;
        public int unknown3 = 0;
        public boolean bypassCache = false;
        public boolean useLivelyVoice = false;
        public String videoTitle = "";

        public byte[] encode() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeString(out, 3, url);
            writeString(out, 4, deviceId);
            writeBool(out, 5, firstRequest);
            writeDouble(out, 6, duration);
            writeInt32(out, 7, unknown0);
            writeString(out, 8, language);
            writeBool(out, 9, forceSourceLang);
            writeInt32(out, 10, unknown1);
            // field 11 (translationHelp) - skipped
            writeBool(out, 13, wasStream);
            writeString(out, 14, responseLanguage);
            writeInt32(out, 15, unknown2);
            writeInt32(out, 16, unknown3);
            writeBool(out, 17, bypassCache);
            writeBool(out, 18, useLivelyVoice);
            writeString(out, 19, videoTitle);
            return out.toByteArray();
        }

        public static TranslationRequest decode(byte[] data) {
            TranslationRequest req = new TranslationRequest();
            ProtoReader reader = new ProtoReader(data);
            while (reader.hasRemaining()) {
                int tag = reader.readTag();
                int fieldNumber = tag >>> 3;
                int wireType = tag & 0x7;
                switch (fieldNumber) {
                    case 3: req.url = reader.readString(); break;
                    case 4: req.deviceId = reader.readString(); break;
                    case 5: req.firstRequest = reader.readBool(); break;
                    case 6: req.duration = reader.readDouble(); break;
                    case 7: req.unknown0 = reader.readInt32(); break;
                    case 8: req.language = reader.readString(); break;
                    case 9: req.forceSourceLang = reader.readBool(); break;
                    case 10: req.unknown1 = reader.readInt32(); break;
                    case 11: reader.readBytes(); break; // skip translationHelp
                    case 13: req.wasStream = reader.readBool(); break;
                    case 14: req.responseLanguage = reader.readString(); break;
                    case 15: req.unknown2 = reader.readInt32(); break;
                    case 16: req.unknown3 = reader.readInt32(); break;
                    case 17: req.bypassCache = reader.readBool(); break;
                    case 18: req.useLivelyVoice = reader.readBool(); break;
                    case 19: req.videoTitle = reader.readString(); break;
                    default: reader.skipField(wireType); break;
                }
            }
            return req;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TranslationRequest)) return false;
            TranslationRequest r = (TranslationRequest) o;
            return firstRequest == r.firstRequest
                    && Double.compare(duration, r.duration) == 0
                    && forceSourceLang == r.forceSourceLang
                    && wasStream == r.wasStream
                    && bypassCache == r.bypassCache
                    && useLivelyVoice == r.useLivelyVoice
                    && unknown0 == r.unknown0
                    && unknown1 == r.unknown1
                    && unknown2 == r.unknown2
                    && unknown3 == r.unknown3
                    && strEquals(url, r.url)
                    && strEquals(deviceId, r.deviceId)
                    && strEquals(language, r.language)
                    && strEquals(responseLanguage, r.responseLanguage)
                    && strEquals(videoTitle, r.videoTitle);
        }

        @Override
        public String toString() {
            return "TranslationRequest{url='" + url + "', language='" + language
                    + "', responseLanguage='" + responseLanguage
                    + "', duration=" + duration
                    + ", firstRequest=" + firstRequest + "}";
        }
    }

    // ===================== Response =====================

    /**
     * VideoTranslationResponse - response from translation API.
     * Field numbers:
     *   1=url, 2=duration(double), 4=status, 5=remainingTime, 6=unknown0,
     *   7=translationId, 8=language, 9=message, 10=isLivelyVoice,
     *   11=unknown2, 12=shouldRetry, 13=unknown3
     */
    public static class TranslationResponse {
        public String url;
        public double duration = 0;
        public int status = 0;
        public int remainingTime = 0;
        public int unknown0 = 0;
        public String translationId = "";
        public String language;
        public String message;
        public boolean isLivelyVoice = false;
        public int unknown2 = 0;
        public int shouldRetry = 0;
        public int unknown3 = 0;

        public byte[] encode() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeString(out, 1, url);
            writeDouble(out, 2, duration);
            writeInt32(out, 4, status);
            writeInt32(out, 5, remainingTime);
            writeInt32(out, 6, unknown0);
            writeString(out, 7, translationId);
            writeString(out, 8, language);
            writeString(out, 9, message);
            writeBool(out, 10, isLivelyVoice);
            writeInt32(out, 11, unknown2);
            writeInt32(out, 12, shouldRetry);
            writeInt32(out, 13, unknown3);
            return out.toByteArray();
        }

        public static TranslationResponse decode(byte[] data) {
            TranslationResponse resp = new TranslationResponse();
            ProtoReader reader = new ProtoReader(data);
            while (reader.hasRemaining()) {
                int tag = reader.readTag();
                int fieldNumber = tag >>> 3;
                int wireType = tag & 0x7;
                switch (fieldNumber) {
                    case 1: resp.url = reader.readString(); break;
                    case 2: resp.duration = reader.readDouble(); break;
                    case 4: resp.status = reader.readInt32(); break;
                    case 5: resp.remainingTime = reader.readInt32(); break;
                    case 6: resp.unknown0 = reader.readInt32(); break;
                    case 7: resp.translationId = reader.readString(); break;
                    case 8: resp.language = reader.readString(); break;
                    case 9: resp.message = reader.readString(); break;
                    case 10: resp.isLivelyVoice = reader.readBool(); break;
                    case 11: resp.unknown2 = reader.readInt32(); break;
                    case 12: resp.shouldRetry = reader.readInt32(); break;
                    case 13: resp.unknown3 = reader.readInt32(); break;
                    default: reader.skipField(wireType); break;
                }
            }
            return resp;
        }

        /** Check if the translation is finished and audio URL is available. */
        public boolean isFinished() {
            return status == VideoTranslationStatus.FINISHED;
        }

        /** Check if the translation is still being processed. */
        public boolean isWaiting() {
            return status == VideoTranslationStatus.WAITING
                    || status == VideoTranslationStatus.LONG_WAITING
                    || status == VideoTranslationStatus.AUDIO_REQUESTED;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TranslationResponse)) return false;
            TranslationResponse r = (TranslationResponse) o;
            return status == r.status
                    && remainingTime == r.remainingTime
                    && Double.compare(duration, r.duration) == 0
                    && isLivelyVoice == r.isLivelyVoice
                    && unknown0 == r.unknown0
                    && unknown2 == r.unknown2
                    && shouldRetry == r.shouldRetry
                    && unknown3 == r.unknown3
                    && strEquals(url, r.url)
                    && strEquals(translationId, r.translationId)
                    && strEquals(language, r.language)
                    && strEquals(message, r.message);
        }

        @Override
        public String toString() {
            return "TranslationResponse{status=" + status + ", url='" + url
                    + "', translationId='" + translationId
                    + "', remainingTime=" + remainingTime + "}";
        }
    }

    // ===================== Status Enum =====================

    /**
     * Translation status codes from the Yandex API.
     */
    public static class VideoTranslationStatus {
        public static final int FAILED = 0;
        public static final int FINISHED = 1;
        public static final int WAITING = 2;
        public static final int LONG_WAITING = 3;
        public static final int PART_CONTENT = 5;
        public static final int AUDIO_REQUESTED = 6;
        public static final int SESSION_REQUIRED = 7;
    }

    // ===================== Convenience Methods =====================

    /**
     * Build a translation request protobuf for the given video.
     *
     * @param videoUrl       The URL of the video to translate
     * @param sourceLang     Source language code (e.g. "en")
     * @param targetLang     Target language code (e.g. "ru")
     * @param duration       Video duration in seconds
     * @param firstRequest   Whether this is the first request for this video
     * @return Encoded protobuf bytes
     */
    public static byte[] buildTranslationRequest(String videoUrl, String sourceLang,
                                                  String targetLang, double duration,
                                                  boolean firstRequest) {
        TranslationRequest req = new TranslationRequest();
        req.url = videoUrl;
        req.language = sourceLang;
        req.responseLanguage = targetLang;
        req.duration = duration;
        req.firstRequest = firstRequest;
        req.unknown0 = 1;
        req.unknown1 = 0;
        return req.encode();
    }

    /**
     * Build a simple translation request (backward compatibility).
     */
    public static byte[] buildTranslationRequest(String videoUrl, String targetLanguage) {
        return buildTranslationRequest(videoUrl, "en", targetLanguage, 0, true);
    }

    /**
     * Parse a translation response from protobuf bytes.
     */
    public static TranslationResponse parseTranslationResponse(byte[] data) {
        return TranslationResponse.decode(data);
    }

    // ===================== Util =====================

    private static boolean strEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
