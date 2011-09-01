/*
 * $Id$
 * $URL$
 */
package com.googlecode.pngtastic.core.processing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import com.googlecode.pngtastic.core.Logger;

/**
 * Implements PNG compression and decompression
 *
 * @author rayvanderborght
 */
public class PngtasticCompressionHandler implements PngCompressionHandler
{
	/** */
	private final Logger log;

	/** */
	private static final List<Integer> compressionStrategies = Arrays.asList(Deflater.DEFAULT_STRATEGY, Deflater.FILTERED, Deflater.HUFFMAN_ONLY);

	/** */
	public PngtasticCompressionHandler(final Logger log)
	{
		this.log = log;
	}

	/**
	 * @inheritDoc
	 */
	public byte[] inflate(final ByteArrayOutputStream imageBytes) throws IOException
	{
		final InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(imageBytes.toByteArray()));
		final ByteArrayOutputStream inflatedOut = new ByteArrayOutputStream();

		int readLength;
		final byte[] block = new byte[8192];
		while ((readLength = inflater.read(block)) != -1)
			inflatedOut.write(block, 0, readLength);

		final byte[] inflatedImageData = inflatedOut.toByteArray();
		return inflatedImageData;
	}

	/**
	 * @inheritDoc
	 */
	public byte[] deflate(final byte[] inflatedImageData, final Integer compressionLevel) throws IOException
	{
		final List<byte[]> results = this.deflateImageDataConcurrently(inflatedImageData, compressionLevel);

		byte[] result = null;
		for (int i = 0; i < results.size(); i++)
		{
			final byte[] data = results.get(i);
			if (result == null || (data.length < result.length))
				result = data;
		}
		this.log.debug("Image bytes=%d", result.length);

		return result;
	}

	/*
	 * Do the work of deflating (compressing) the image data with the
	 * different compression strategies in separate threads to take
	 * advantage of multiple core architectures.
	 */
	private List<byte[]> deflateImageDataConcurrently(final byte[] inflatedImageData, final Integer compressionLevel)
	{
		final Collection<byte[]> results = new ConcurrentLinkedQueue<byte[]>();

		final Collection<Callable<Object>> tasks = new ArrayList<Callable<Object>>();
		for (final int strategy : compressionStrategies)
		{
			tasks.add(Executors.callable(new Runnable()
			{
				public void run()
				{
					try
					{
						results.add(PngtasticCompressionHandler.this.deflateImageData(inflatedImageData, strategy, compressionLevel));
					}
					catch (final Throwable e)
					{
						PngtasticCompressionHandler.this.log.error("Uncaught Exception: %s", e.getMessage());
					}
				}
			}));
		}

		final ExecutorService compressionThreadPool = Executors.newFixedThreadPool(compressionStrategies.size());
		try
		{
			compressionThreadPool.invokeAll(tasks);
		}
		catch (final InterruptedException ex) {  }
		finally
		{
			compressionThreadPool.shutdown();
		}

		return new ArrayList<byte[]>(results);
	}

	/* */
	private byte[] deflateImageData(final byte[] inflatedImageData, final int strategy, final Integer compressionLevel) throws IOException
	{
		byte[] result = null;
		int bestCompression = Deflater.BEST_COMPRESSION;

		if (compressionLevel == null || compressionLevel > Deflater.BEST_COMPRESSION || compressionLevel < Deflater.NO_COMPRESSION)
		{
			for (int compression = Deflater.BEST_COMPRESSION; compression > Deflater.NO_COMPRESSION; compression--)
			{
				final ByteArrayOutputStream deflatedOut = this.deflate(inflatedImageData, strategy, compression);

				if (result == null || (result.length > deflatedOut.size()))
				{
					result = deflatedOut.toByteArray();
					bestCompression = compression;
				}
			}
		}
		else
		{
			result = this.deflate(inflatedImageData, strategy, compressionLevel).toByteArray();
			bestCompression = compressionLevel;
		}
		this.log.debug("Compression strategy: %s, compression level=%d, bytes=%d", strategy, bestCompression, result.length);

		return result;
	}

	/* */
	private ByteArrayOutputStream deflate(final byte[] inflatedImageData, final int strategy, final int compression) throws IOException
	{
		final ByteArrayOutputStream deflatedOut = new ByteArrayOutputStream();
		final Deflater deflater = new Deflater(compression);
		deflater.setStrategy(strategy);

		final DeflaterOutputStream stream = new DeflaterOutputStream(deflatedOut, deflater);
		stream.write(inflatedImageData);
		stream.close();

		return deflatedOut;
	}
}
