/*
 * $Id: PngtasticInterlaceHandler.java 31 2010-09-19 19:32:29Z voidstar $
 * $URL: http://pngtastic.googlecode.com/svn/trunk/pngtastic/src/com/googlecode/pngtastic/core/processing/PngtasticInterlaceHandler.java $
 */
package com.googlecode.pngtastic.core.processing;

import java.util.ArrayList;
import java.util.List;

import com.googlecode.pngtastic.core.Logger;
import com.googlecode.pngtastic.core.PngException;

/**
 * Implement PNG interlacing and deinterlacing
 *
 * @author rayvanderborght
 */
public class PngtasticInterlaceHandler implements PngInterlaceHandler
{
	/** */
	private final Logger log;

	/** */
	private final PngFilterHandler pngFilterHandler;

	/** */
	private static final int[] interlaceColumnFrequency	= new int[] { 8, 8, 4, 4, 2, 2, 1 };
	private static final int[] interlaceColumnOffset	= new int[] { 0, 4, 0, 2, 0, 1, 0 };
	private static final int[] interlaceRowFrequency	= new int[] { 8, 8, 8, 4, 4, 2, 2 };
	private static final int[] interlaceRowOffset		= new int[] { 0, 0, 4, 0, 2, 0, 1 };

	/** */
	public PngtasticInterlaceHandler(final Logger log, final PngFilterHandler pngFilterHandler)
	{
		this.log = log;
		this.pngFilterHandler = pngFilterHandler;
	}

	/**
	 * @inheritDoc
	 *
	 * Throws a runtime exception.
	 * <p>
	 * NOTE: This is left unimplemented currently.  Interlacing should make
	 * most images larger in filesize, so pngtastic currently deinterlaces
	 * all images passed through it.  There may be rare exceptions that
	 * actually benefit from interlacing, so there may come a time to revisit
	 * this.
	 */
	public List<byte[]> interlace(final int width, final int height, final int sampleBitCount, final byte[] inflatedImageData) {
		throw new RuntimeException("Not implemented");
	}

	/**
	 * @inheritDoc
	 */
	public List<byte[]> deInterlace(final int width, final int height, final int sampleBitCount, final byte[] inflatedImageData)
	{
		this.log.debug("Deinterlacing");

		final List<byte[]> results = new ArrayList<byte[]>();
		final int sampleSize = Math.max(1, sampleBitCount / 8);
		final byte[][] rows = new byte[height][Double.valueOf(Math.ceil(width * sampleBitCount / 8D)).intValue() + 1];

		int subImageOffset = 0;
		for (int pass = 0; pass < 7; pass++)
		{
			final int subImageRows = height / interlaceRowFrequency[pass];
			final int subImageColumns = width / interlaceColumnFrequency[pass];
			final int rowLength = Double.valueOf(Math.ceil(subImageColumns * sampleBitCount / 8D)).intValue() + 1;

			byte[] previousRow = new byte[rowLength];
			int offset = 0;
			for (int i = 0; i < subImageRows; i++)
			{
				offset = subImageOffset + i * rowLength;
				final byte[] row = new byte[rowLength];
				System.arraycopy(inflatedImageData, offset, row, 0, rowLength);
				try
				{
					this.pngFilterHandler.deFilter(row, previousRow, sampleBitCount);
				}
				catch (final PngException e)
				{
					this.log.error("Error: %s", e.getMessage());
				}

				final int samples = (row.length - 1) / sampleSize;
				for (int sample = 0; sample < samples; sample++)
				{
					for (int b = 0; b < sampleSize; b++)
					{
						final int cf = interlaceColumnFrequency[pass] * sampleSize;
						final int co = interlaceColumnOffset[pass] * sampleSize;
						final int rf = interlaceRowFrequency[pass];
						final int ro = interlaceRowOffset[pass];
						rows[i * rf + ro][sample * cf + co + b + 1] = row[(sample * sampleSize) + b + 1];
					}
				}
				previousRow = row.clone();
			}
			subImageOffset = offset + rowLength;
		}
		for (int i = 0; i < rows.length; i++)
		{
			results.add(rows[i]);
		}

		return results;
	}
}
