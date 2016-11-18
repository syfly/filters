package jp.co.cyberagent.android.gpuimage.util;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.graphics.Bitmap;

public class MyBitmap {

	public static Bitmap createMyBitmap(byte[] data, int width, int height, int rotate) {
		int[] colors = convertByteToColor(data, width, height, rotate);
		if (colors == null) {
			return null;
		}
		Bitmap bmp = null;
		try {
			if (rotate == 90 || rotate == 270)
				bmp = Bitmap.createBitmap(colors, 0, height, height, width, Bitmap.Config.ARGB_8888);
			else
				bmp = Bitmap.createBitmap(colors, 0, width, width, height, Bitmap.Config.ARGB_8888);
		} catch (Exception e) {
			return null;
		}
		return bmp;
	}

	public static int convertByteToInt(byte data) {

		int heightBit = (int) ((data >> 4) & 0x0F);
		int lowBit = (int) (0x0F & data);
		return heightBit * 16 + lowBit;
	}

	public static int[] convertByteToColor(byte[] data, int width, int height, int rotate) {
		int size = data.length;
		if (size == 0) {
			return null;
		}

		int arg = 0;
		if (size % 4 != 0) {
			arg = 1;
		}

		int[] color = new int[size / 4 + arg];
		int red, green, blue, alpha, swapUpIndex, _tempIndex, swapDownIndex, swapPoint;
		int colorIndex = 0, dataIndex = 0;
		switch (rotate) {
		case 0:
			for (int y = 0; y < height; ++y) {
				for (int x = 0; x < width; ++x) {
					swapUpIndex = y * width + x;
					swapDownIndex = (height - y - 1) * width + x;
					if (swapUpIndex < color.length && swapDownIndex < color.length) {
						_tempIndex = swapUpIndex * 4;
						red = convertByteToInt(data[_tempIndex]);
						green = convertByteToInt(data[_tempIndex + 1]);
						blue = convertByteToInt(data[_tempIndex + 2]);
						alpha = convertByteToInt(data[_tempIndex + 3]);

						color[swapDownIndex] = (alpha << 24) | (red << 16) | (green << 8) | blue | 0xFF000000;
					}

				}
			}
			break;
		case 180:
			for (int y = 0; y < height; ++y) {
				for (int x = 0; x < width; ++x) {
					swapUpIndex = y * width + x;
					swapDownIndex = y * width + width-x;
					if (swapUpIndex < color.length && swapDownIndex < color.length) {
						_tempIndex = swapUpIndex * 4;
						red = convertByteToInt(data[_tempIndex]);
						green = convertByteToInt(data[_tempIndex + 1]);
						blue = convertByteToInt(data[_tempIndex + 2]);
						alpha = convertByteToInt(data[_tempIndex + 3]);

						color[swapDownIndex] = (alpha << 24) | (red << 16) | (green << 8) | blue | 0xFF000000;
					}

				}
			}
			break;
		case 90:
			for (int y = 0; y < height; ++y) {
				for (int x = 0; x < width; ++x) {
					colorIndex = x * height + y;
					if (dataIndex < color.length && colorIndex < color.length) {
						_tempIndex = dataIndex * 4;
						red = convertByteToInt(data[_tempIndex]);
						green = convertByteToInt(data[_tempIndex + 1]);
						blue = convertByteToInt(data[_tempIndex + 2]);
						alpha = convertByteToInt(data[_tempIndex + 3]);
						dataIndex++;
						color[colorIndex] = (alpha << 24) | (red << 16) | (green << 8) | blue | 0xFF000000;
					}
				}
			}
			break;
		case 270:
			for (int y = height - 1; y >= 0; --y) {
				for (int x = width - 1; x >= 0; --x) {
					colorIndex = x * height + y;
					if (dataIndex < color.length && colorIndex < color.length) {
						_tempIndex = dataIndex * 4;
						red = convertByteToInt(data[_tempIndex]);
						green = convertByteToInt(data[_tempIndex + 1]);
						blue = convertByteToInt(data[_tempIndex + 2]);
						alpha = convertByteToInt(data[_tempIndex + 3]);
						dataIndex++;
						color[colorIndex] = (alpha << 24) | (red << 16) | (green << 8) | blue | 0xFF000000;
					}

				}
			}
			break;
		default:
			break;
		}

		// for (int i =0; i <color.length ; ++i) {
		// red = convertByteToInt(data[i * 4]);
		// green = convertByteToInt(data[i * 4 + 1]);
		// blue = convertByteToInt(data[i * 4 + 2]);
		// alpha= convertByteToInt(data[i * 4 + 3]);
		//
		// color[color.length-i-1] = (alpha << 24) |(red << 16) | (green << 8) | blue | 0xFF000000;
		// }

		return color;
	}
	
	public static boolean compressBitmap(Bitmap bm, Bitmap.CompressFormat format, String localPath, int qulity) {
        try {
            FileOutputStream fos = new FileOutputStream(localPath);
            boolean ret = bm.compress(format, qulity, fos);
            fos.flush();
            fos.close();
            return ret;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
