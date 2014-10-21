package com.qiniu;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Base64;

public class QETag {
	private final int SLICE_SIZE = 1 << 22;

	public byte[] sha1(byte[] data) throws NoSuchAlgorithmException {
		MessageDigest mDigest = MessageDigest.getInstance("sha1");
		return mDigest.digest(data);
	}

	public String urlSafeBase64Encode(byte[] data) {
		byte[] encodedData = Base64.encodeBase64(data);
		String encodedString = new String(encodedData);
		encodedString = encodedString.replace('+', '-').replace('/', '_');
		return encodedString;
	}

	public String calcETag(String fileName) throws IOException,
			NoSuchAlgorithmException {
		String etag = "";
		File file = new File(fileName);
		if (!(file.exists() && file.isFile() && file.canRead())) {
			System.err.println("Error: File not found or not readable");
			return etag;
		}
		long fileLength = file.length();
		FileInputStream inputStream = new FileInputStream(file);
		if (fileLength <= SLICE_SIZE) {
			byte[] fileData = new byte[(int) fileLength];
			inputStream.read(fileData, 0, (int) fileLength);
			byte[] sha1Data = sha1(fileData);
			int sha1DataLen = sha1Data.length;
			byte[] hashData = new byte[sha1DataLen + 1];
			System.arraycopy(sha1Data, 0, hashData, 1, sha1DataLen);
			hashData[0] = 0x16;
			etag = urlSafeBase64Encode(hashData);
		} else {
			int sliceCount = (int) (fileLength / SLICE_SIZE);
			if (fileLength % SLICE_SIZE != 0) {
				sliceCount += 1;
			}
			int[] sliceSizes = new int[sliceCount];
			int sliceSizeIndex = 0;
			for (int i = 0; i < sliceCount; i++) {
				int sliceSize = SLICE_SIZE;
				if (i == sliceCount - 1) {
					sliceSize = (int) (fileLength - (sliceCount - 1)
							* SLICE_SIZE);
				}
				sliceSizes[sliceSizeIndex] = sliceSize;
				sliceSizeIndex++;
			}
			byte[] sha1AllData = new byte[0];
			for (int i = 0; i < sliceSizes.length; i++) {
				int sliceSize = sliceSizes[i];
				byte[] sliceData = new byte[sliceSize];
				inputStream.read(sliceData, 0, sliceSize);

				byte[] sha1Data = sha1(sliceData);
				int sha1AllDataLen = sha1AllData.length;
				int sha1DataLen = sha1Data.length;
				int totalSize = sha1AllDataLen + sha1DataLen;
				byte[] tmpSha1AllData = new byte[totalSize];
				System.arraycopy(sha1AllData, 0, tmpSha1AllData, 0,
						sha1AllDataLen);
				System.arraycopy(sha1Data, 0, tmpSha1AllData, sha1AllDataLen,
						sha1DataLen);
				sha1AllData = tmpSha1AllData;
			}
			byte[] sha1TwiceData = sha1(sha1AllData);
			int sha1TwiceLen = sha1TwiceData.length;
			byte[] hashData = new byte[sha1TwiceLen + 1];
			System.arraycopy(sha1TwiceData, 0, hashData, 1, sha1TwiceLen);
			hashData[0] = (byte) 0x96;
			etag = urlSafeBase64Encode(hashData);
		}
		inputStream.close();
		return etag;
	}

	public static void main(String[] args) {
		int argsCount = args.length;
		if (argsCount != 2) {
			System.out.println("Usage: qetag <filename>");
		} else {
			String fileName = args[1];
			QETag etag = new QETag();
			try {
				System.out.println(etag.calcETag(fileName));
			} catch (NoSuchAlgorithmException ex) {
				System.err.println("Unsupported algorithm:" + ex.getMessage());
			} catch (IOException ex) {
				System.err.println("IO Error:" + ex.getMessage());
			}
		}
	}

}