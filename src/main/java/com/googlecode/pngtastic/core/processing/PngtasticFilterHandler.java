/*
 * $Id: PngtasticFilterHandler.java 34 2010-09-23 04:42:20Z voidstar $ $URL:
 * http://pngtastic.googlecode.com/svn/trunk/pngtastic
 * /src/com/googlecode/pngtastic/core/processing/PngtasticFilterHandler.java $
 */
package com.googlecode.pngtastic.core.processing;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.googlecode.pngtastic.core.Logger;
import com.googlecode.pngtastic.core.PngException;
import com.googlecode.pngtastic.core.PngFilterType;


/**
 * Implement PNG filtering and defiltering
 *
 * @author rayvanderborght
 */
public class PngtasticFilterHandler
        implements PngFilterHandler {
    /** */
    private final Logger log;

    /** */
    public PngtasticFilterHandler(final Logger log) {
        this.log = log;
    }

    /**
     * @inheritDoc
     */
    public void applyFiltering(final PngFilterType filterType, final List<byte[]> scanlines, final int sampleBitCount) {
        final int scanlineLength = scanlines.get(0).length;
        byte[] previousRow = new byte[scanlineLength];
        for (final byte[] scanline : scanlines) {
            if (filterType != null)
                scanline[0] = filterType.getValue();

            final byte[] previous = scanline.clone();

            try {
                this.filter(scanline, previousRow, sampleBitCount);
            } catch (final PngException e) {
                this.log.error("Error during filtering: %s", e.getMessage());
            }
            previousRow = previous;
        }
    }

    /**
     * @inheritDoc
     */
    public void applyAdaptiveFiltering(final byte[] inflatedImageData, final List<byte[]> scanlines,
            final Map<PngFilterType, List<byte[]>> filteredScanLines, final int sampleSize)
            throws IOException {
        for (int s = 0; s < scanlines.size(); s++) {
            long bestSum = Long.MAX_VALUE;
            PngFilterType bestFilterType = null;
            for (final Map.Entry<PngFilterType, List<byte[]>> entry : filteredScanLines.entrySet()) {
                long sum = 0;
                final byte[] scanline = entry.getValue().get(s);
                for (int i = 1; i < scanline.length; i++)
                    sum += Math.abs(scanline[i]);

                if (sum < bestSum) {
                    bestFilterType = entry.getKey();
                    bestSum = sum;
                }
            }
            scanlines.get(s)[0] = bestFilterType.getValue();
        }

        this.applyFiltering(null, scanlines, sampleSize);
    }

    /**
     * @inheritDoc The bytes are named as follows (x = current, a = previous, b = above, c = previous and above)
     *
     *             <pre>
     * c b
     * a x
     * </pre>
     */
    public void filter(final byte[] line, final byte[] previousLine, final int sampleBitCount)
            throws PngException {
        final PngFilterType filterType = PngFilterType.forValue(line[0]);
        line[0] = 0;

        final PngFilterType previousFilterType = PngFilterType.forValue(previousLine[0]);
        previousLine[0] = 0;

        switch (filterType) {
            case NONE:
                break;

            case SUB: {
                final byte[] original = line.clone();
                final int previous = -(Math.max(1, sampleBitCount / 8) - 1);
                for (int x = 1, a = previous; x < line.length; x++, a++)
                    line[x] = (byte) (original[x] - ((a < 0) ? 0 : original[a]));
                break;
            }
            case UP: {
                for (int x = 1; x < line.length; x++)
                    line[x] = (byte) (line[x] - previousLine[x]);
                break;
            }
            case AVERAGE: {
                final byte[] original = line.clone();
                final int previous = -(Math.max(1, sampleBitCount / 8) - 1);
                for (int x = 1, a = previous; x < line.length; x++, a++)
                    line[x] = (byte) (original[x] - ((0xFF & original[(a < 0) ? 0 : a]) + (0xFF & previousLine[x])) / 2);
                break;
            }
            case PAETH: {
                final byte[] original = line.clone();
                final int previous = -(Math.max(1, sampleBitCount / 8) - 1);
                for (int x = 1, a = previous; x < line.length; x++, a++) {
                    final int result = this.paethPredictor(original, previousLine, x, a);
                    line[x] = (byte) (original[x] - result);
                }
                break;
            }
            default:
                throw new PngException("Unrecognized filter type " + filterType);
        }
        line[0] = filterType.getValue();
        previousLine[0] = previousFilterType.getValue();
    }

    /**
     * @inheritDoc
     */
    public void deFilter(final byte[] line, final byte[] previousLine, final int sampleBitCount)
            throws PngException {
        final PngFilterType filterType = PngFilterType.forValue(line[0]);
        line[0] = 0;

        final PngFilterType previousFilterType = PngFilterType.forValue(previousLine[0]);
        previousLine[0] = 0;

        switch (filterType) {
            case SUB: {
                final int previous = -(Math.max(1, sampleBitCount / 8) - 1);
                for (int x = 1, a = previous; x < line.length; x++, a++)
                    line[x] = (byte) (line[x] + ((a < 0) ? 0 : line[a]));
                break;
            }
            case UP: {
                for (int x = 1; x < line.length; x++)
                    line[x] = (byte) (line[x] + previousLine[x]);
                break;
            }
            case AVERAGE: {
                final int previous = -(Math.max(1, sampleBitCount / 8) - 1);
                for (int x = 1, a = previous; x < line.length; x++, a++)
                    line[x] = (byte) (line[x] + ((0xFF & ((a < 0) ? 0 : line[a])) + (0xFF & previousLine[x])) / 2);
                break;
            }
            case PAETH: {
                final int previous = -(Math.max(1, sampleBitCount / 8) - 1);
                for (int x = 1, xp = previous; x < line.length; x++, xp++) {
                    final int result = this.paethPredictor(line, previousLine, x, xp);
                    line[x] = (byte) (line[x] + result);
                }
                break;
            }
        }
        line[0] = filterType.getValue();
        previousLine[0] = previousFilterType.getValue();
    }

    /* */
    private int paethPredictor(final byte[] line, final byte[] previousLine, final int x, final int xp) {
        final int a = 0xFF & ((xp < 0) ? 0 : line[xp]);
        final int b = 0xFF & previousLine[x];
        final int c = 0xFF & ((xp < 0) ? 0 : previousLine[xp]);
        final int p = a + b - c;

        final int pa = (p >= a) ? (p - a) : -(p - a);
        final int pb = (p >= b) ? (p - b) : -(p - b);
        final int pc = (p >= c) ? (p - c) : -(p - c);

        if (pa <= pb && pa <= pc)
            return a;

        return (pb <= pc) ? b : c;
    }
}
