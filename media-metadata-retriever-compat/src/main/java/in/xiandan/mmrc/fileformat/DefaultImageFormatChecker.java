/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package in.xiandan.mmrc.fileformat;

import android.support.annotation.Nullable;

import java.io.InputStream;

import in.xiandan.mmrc.utils.Ints;
import in.xiandan.mmrc.utils.Preconditions;

/**
 * Default image format checker that is able to determine all {@link DefaultImageFormats}.
 */
public class DefaultImageFormatChecker implements FileFormat.FormatChecker {

    /**
     * Maximum header size for any image type.
     *
     * <p>This determines how much data {@link FileFormatChecker#getFileFormat(InputStream) reads
     * from a stream. After changing any of the type detection algorithms, or adding a new one, this
     * value should be edited.
     */
    final int MAX_HEADER_LENGTH =
            Ints.max(
                    EXTENDED_WEBP_HEADER_LENGTH,
                    SIMPLE_WEBP_HEADER_LENGTH,
                    JPEG_HEADER_LENGTH,
                    PNG_HEADER_LENGTH,
                    APNG_HEADER_LENGTH,
                    GIF_HEADER_LENGTH,
                    BMP_HEADER_LENGTH,
                    ICO_HEADER_LENGTH,
                    HEIF_HEADER_LENGTH);

    @Override
    public int getHeaderSize() {
        return MAX_HEADER_LENGTH;
    }

    /**
     * Tries to match imageHeaderByte and headerSize against every known image format. If any match
     * succeeds, corresponding FileFormat is returned.
     *
     * @param headerBytes the header bytes to check
     * @param headerSize  the available header size
     * @return FileFormat for given imageHeaderBytes or UNKNOWN_IMAGE if no such type could be recognized
     */
    @Nullable
    @Override
    public final FileFormat determineFormat(byte[] headerBytes, int headerSize) {
        Preconditions.checkNotNull(headerBytes);

        if (WebpSupportStatus.isWebpHeader(headerBytes, 0, headerSize)) {
            return getWebpFormat(headerBytes, headerSize);
        }

        if (isJpegHeader(headerBytes, headerSize)) {
            return DefaultImageFormats.JPEG;
        }

        if (isPngHeader(headerBytes, headerSize)) {
            return getPNGFormat(headerBytes, headerSize);
        }

        if (isGifHeader(headerBytes, headerSize)) {
            return DefaultImageFormats.GIF;
        }

        if (isBmpHeader(headerBytes, headerSize)) {
            return DefaultImageFormats.BMP;
        }

        if (isIcoHeader(headerBytes, headerSize)) {
            return DefaultImageFormats.ICO;
        }

        if (isHeifHeader(headerBytes, headerSize)) {
            return DefaultImageFormats.HEIF;
        }

        return FileFormat.UNKNOWN;
    }

    /**
     * Each WebP header should consist of at least 20 bytes and start
     * with "RIFF" bytes followed by some 4 bytes and "WEBP" bytes.
     * More detailed description if WebP can be found here:
     * <a href="https://developers.google.com/speed/webp/docs/riff_container">
     * https://developers.google.com/speed/webp/docs/riff_container</a>
     */
    private static final int SIMPLE_WEBP_HEADER_LENGTH = 20;

    /**
     * Each VP8X WebP image has "features" byte following its ChunkHeader('VP8X')
     */
    private static final int EXTENDED_WEBP_HEADER_LENGTH = 21;

    /**
     * Determines type of WebP image. imageHeaderBytes has to be header of a WebP image
     */
    private static FileFormat getWebpFormat(final byte[] imageHeaderBytes, final int headerSize) {
        Preconditions.checkArgument(WebpSupportStatus.isWebpHeader(imageHeaderBytes, 0, headerSize));
        if (WebpSupportStatus.isSimpleWebpHeader(imageHeaderBytes, 0)) {
            return DefaultImageFormats.WEBP_SIMPLE;
        }

        if (WebpSupportStatus.isLosslessWebpHeader(imageHeaderBytes, 0)) {
            return DefaultImageFormats.WEBP_LOSSLESS;
        }

        if (WebpSupportStatus.isExtendedWebpHeader(imageHeaderBytes, 0, headerSize)) {
            if (WebpSupportStatus.isAnimatedWebpHeader(imageHeaderBytes, 0)) {
                return DefaultImageFormats.WEBP_ANIMATED;
            }
            if (WebpSupportStatus.isExtendedWebpHeaderWithAlpha(imageHeaderBytes, 0)) {
                return DefaultImageFormats.WEBP_EXTENDED_WITH_ALPHA;
            }
            return DefaultImageFormats.WEBP_EXTENDED;
        }

        return FileFormat.UNKNOWN;
    }

    /**
     * Every JPEG image should start with SOI mark (0xFF, 0xD8) followed by beginning
     * of another segment (0xFF)
     */
    private static final byte[] JPEG_HEADER = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final int JPEG_HEADER_LENGTH = JPEG_HEADER.length;

    /**
     * Checks if imageHeaderBytes starts with SOI (start of image) marker, followed by 0xFF.
     * If headerSize is lower than 3 false is returned.
     * Description of jpeg format can be found here:
     * <a href="http://www.w3.org/Graphics/JPEG/itu-t81.pdf">
     * http://www.w3.org/Graphics/JPEG/itu-t81.pdf</a>
     * Annex B deals with compressed data format
     *
     * @param imageHeaderBytes
     * @param headerSize
     * @return true if imageHeaderBytes starts with SOI_BYTES and headerSize >= 3
     */
    private static boolean isJpegHeader(final byte[] imageHeaderBytes, final int headerSize) {
        return headerSize >= JPEG_HEADER.length &&
                FileFormatCheckerUtils.startsWithPattern(imageHeaderBytes, JPEG_HEADER);
    }

    /**
     * Every PNG image starts with 8 byte signature consisting of
     * following bytes
     */
    private static final byte[] PNG_HEADER = new byte[]{
            (byte) 0x89,
            'P', 'N', 'G',
            (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A};
    private static final int PNG_HEADER_LENGTH = PNG_HEADER.length;

    /**
     * Checks if array consisting of first headerSize bytes of imageHeaderBytes
     * starts with png signature. More information on PNG can be found there:
     * <a href="http://en.wikipedia.org/wiki/Portable_Network_Graphics">
     * http://en.wikipedia.org/wiki/Portable_Network_Graphics</a>
     *
     * @param imageHeaderBytes
     * @param headerSize
     * @return true if imageHeaderBytes starts with PNG_HEADER
     */
    private static boolean isPngHeader(final byte[] imageHeaderBytes, final int headerSize) {
        return headerSize >= PNG_HEADER.length &&
                FileFormatCheckerUtils.startsWithPattern(imageHeaderBytes, PNG_HEADER);
    }

    private static final byte[] APNG_BYTES = FileFormatCheckerUtils.asciiBytes("acTL");
    private static final int APNG_HEADER_LENGTH = 37 + APNG_BYTES.length;

    private static FileFormat getPNGFormat(final byte[] imageHeaderBytes, final int headerSize) {
        if (matchBytePattern(imageHeaderBytes, APNG_HEADER_LENGTH - APNG_BYTES.length, APNG_BYTES)) {
            return DefaultImageFormats.PNG_ANIMATED;
        }
        return DefaultImageFormats.PNG;
    }

    private static boolean matchBytePattern(
            final byte[] byteArray,
            final int offset,
            final byte[] pattern) {
        if (pattern == null || byteArray == null) {
            return false;
        }
        if (pattern.length + offset > byteArray.length) {
            return false;
        }

        for (int i = 0; i < pattern.length; ++i) {
            if (byteArray[i + offset] != pattern[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Every gif image starts with "GIF" bytes followed by
     * bytes indicating version of gif standard
     */
    private static final byte[] GIF_HEADER_87A = FileFormatCheckerUtils.asciiBytes("GIF87a");
    private static final byte[] GIF_HEADER_89A = FileFormatCheckerUtils.asciiBytes("GIF89a");
    private static final int GIF_HEADER_LENGTH = 6;

    /**
     * Checks if first headerSize bytes of imageHeaderBytes constitute a valid header for a gif image.
     * Details on GIF header can be found <a href="http://www.w3.org/Graphics/GIF/spec-gif89a.txt">
     * on page 7</a>
     *
     * @param imageHeaderBytes
     * @param headerSize
     * @return true if imageHeaderBytes is a valid header for a gif image
     */
    private static boolean isGifHeader(final byte[] imageHeaderBytes, final int headerSize) {
        if (headerSize < GIF_HEADER_LENGTH) {
            return false;
        }
        return FileFormatCheckerUtils.startsWithPattern(imageHeaderBytes, GIF_HEADER_87A) ||
                FileFormatCheckerUtils.startsWithPattern(imageHeaderBytes, GIF_HEADER_89A);
    }

    /**
     * Every bmp image starts with "BM" bytes
     */
    private static final byte[] BMP_HEADER = FileFormatCheckerUtils.asciiBytes("BM");
    private static final int BMP_HEADER_LENGTH = BMP_HEADER.length;

    /**
     * Checks if first headerSize bytes of imageHeaderBytes constitute a valid header for a bmp image.
     * Details on BMP header can be found <a href="http://www.onicos.com/staff/iz/formats/bmp.html">
     * </a>
     *
     * @param imageHeaderBytes
     * @param headerSize
     * @return true if imageHeaderBytes is a valid header for a bmp image
     */
    private static boolean isBmpHeader(final byte[] imageHeaderBytes, final int headerSize) {
        if (headerSize < BMP_HEADER.length) {
            return false;
        }
        return FileFormatCheckerUtils.startsWithPattern(imageHeaderBytes, BMP_HEADER);
    }

    /**
     * Every ico image starts with 0x00000100 bytes
     */
    private static final byte[] ICO_HEADER =
            new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00};

    private static final int ICO_HEADER_LENGTH = ICO_HEADER.length;

    /**
     * Checks if first headerSize bytes of imageHeaderBytes constitute a valid header for a ico image.
     * Details on ICO header can be found <a href="https://en.wikipedia.org/wiki/ICO_(file_format)">
     * </a>
     *
     * @param imageHeaderBytes
     * @param headerSize
     * @return true if imageHeaderBytes is a valid header for a ico image
     */
    private static boolean isIcoHeader(final byte[] imageHeaderBytes, final int headerSize) {
        if (headerSize < ICO_HEADER.length) {
            return false;
        }
        return FileFormatCheckerUtils.startsWithPattern(imageHeaderBytes, ICO_HEADER);
    }

    /**
     * Every HEIF image starts with a specific signature. It's guaranteed to include "ftyp". The 4th
     * byte of the header gives us the size of the box, then we have "ftyp" followed by the exact
     * image format which can be one of: heic, heix, hevc, hevx.
     */
    private static final String HEIF_HEADER_PREFIX = "ftyp";

    private static final String[] HEIF_HEADER_SUFFIXES = {
            "heic", "heix", "hevc", "hevx", "mif1", "msf1"
    };
    private static final int HEIF_HEADER_LENGTH =
            FileFormatCheckerUtils.asciiBytes(HEIF_HEADER_PREFIX + HEIF_HEADER_SUFFIXES[0]).length;

    /**
     * Checks if first headerSize bytes of imageHeaderBytes constitute a valid header for a HEIF
     * image. Details on HEIF header can be found at: <a
     * href="http://nokiatech.github.io/heif/technical.html"></a>
     *
     * @param imageHeaderBytes
     * @param headerSize
     * @return true if imageHeaderBytes is a valid header for a HEIF image
     */
    private static boolean isHeifHeader(final byte[] imageHeaderBytes, final int headerSize) {
        if (headerSize < HEIF_HEADER_LENGTH) {
            return false;
        }

        final byte boxLength = imageHeaderBytes[3];
        if (boxLength < 8) {
            return false;
        }

        for (final String heifFtype : HEIF_HEADER_SUFFIXES) {
            final int indexOfHeaderPattern =
                    FileFormatCheckerUtils.indexOfPattern(
                            imageHeaderBytes,
                            imageHeaderBytes.length,
                            FileFormatCheckerUtils.asciiBytes(HEIF_HEADER_PREFIX + heifFtype),
                            HEIF_HEADER_LENGTH);
            if (indexOfHeaderPattern > -1) {
                return true;
            }
        }

        return false;
    }
}
