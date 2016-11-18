package jp.co.cyberagent.android.gpuimage.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;


import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

public class VideoDump {

	class VideoDumpConfig {
        // Currently we are running with a local copy of the video.
        // It should work with a "http://" sort of streaming url as well.
//		new File(Environment.getExternalStoragePublicDirectory(
//                Environment.DIRECTORY_PICTURES), "mediadump");
        public static final String VIDEO_URI = "/sdcard/Pictures/mediadump/sample.mp4";
        public static final String ROOT_DIR = "/sdcard/Pictures/mediadump/";
        public static final String IMAGES_LIST = "images.lst";
        public static final String IMAGE_PREFIX = "img";
        public static final String IMAGE_SUFFIX = ".png";
        public static final String PROPERTY_FILE = "prop.xml";

        // So far, glReadPixels only supports two (format, type) combinations
        //     GL_RGB  GL_UNSIGNED_SHORT_5_6_5   16 bits per pixel (default)
        //     GL_RGBA GL_UNSIGNED_BYTE          32 bits per pixel
        public static final int PIXEL_FORMAT = GLES20.GL_RGBA;
        public static final int PIXEL_TYPE = PIXEL_FORMAT == GLES20.GL_RGBA
                ? GLES20.GL_UNSIGNED_BYTE : GLES20.GL_UNSIGNED_SHORT_5_6_5;
        public static final int BYTES_PER_PIXEL =
                PIXEL_FORMAT == GLES20.GL_RGBA ? 4 : 2;
        public static final boolean SET_CHOOSER
                = PIXEL_FORMAT == GLES20.GL_RGBA ? true : false;

        // On Motorola Xoom, it takes 100ms to read pixels and 180ms to write to a file
        // to dump a complete 720p(1280*720) video frame. It's much slower than the frame
        // playback interval (40ms). So we only dump a center block and it should be able
        // to catch all the e2e distortion. A reasonable size of the block is 256x256,
        // which takes 4ms to read pixels and 25 ms to write to a file.
        public static final int MAX_DUMP_WIDTH = 256;
        public static final int MAX_DUMP_HEIGHT = 256;

        // TODO: MediaPlayer doesn't give back the video frame rate and we'll need to
        // figure it by dividing the total number of frames by the duration.
        public static final int FRAME_RATE = 25;
    }
	
	
	class RGBFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return (name.endsWith(VideoDumpConfig.IMAGE_SUFFIX));
        }
    }

	private BufferedWriter mImageListWriter;
	private int mWidth;
	private int mHeight;
	private int mStartX;
	private int mStartY;
	private ByteBuffer mBuffer;
	private int mFrameNumber;

	public VideoDump() {
		File dump_dir = new File(VideoDumpConfig.ROOT_DIR);
        File[] dump_files = dump_dir.listFiles(new RGBFilter());
//        if (dump_files != null)
//        {
//        	for (File dump_file :dump_files) {
//                dump_file.delete();
//            }
//        }
        

        File image_list = new File(VideoDumpConfig.ROOT_DIR
                                   + VideoDumpConfig.IMAGES_LIST);
        image_list.delete();
        try {
			mImageListWriter = new BufferedWriter(new FileWriter(image_list));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public BufferedWriter setImageListWriter() {
        return mImageListWriter;
    }
	
	public void pause() {
        try {
            mImageListWriter.flush();
        } catch (java.io.IOException e) {
        	e.printStackTrace();
        }
    }

    public void stopPlayback() {
        if (mImageListWriter != null) {
            try {
                mImageListWriter.flush();
                mImageListWriter.close();
            } catch (java.io.IOException e) {
            }
        }
    }
    
    public void init(int video_width, int video_height) {
    	mWidth = Math.min(VideoDumpConfig.MAX_DUMP_WIDTH, video_width);
        mHeight = Math.min(VideoDumpConfig.MAX_DUMP_HEIGHT, video_height);
        mStartX = video_width / mWidth / 2 * mWidth;
        mStartY = video_height / mHeight / 2 * mHeight;
        
    	int image_size = mWidth * mHeight * VideoDumpConfig.BYTES_PER_PIXEL;
        mBuffer = ByteBuffer.allocate(image_size);
        
        mFrameNumber = 0;
    }
    
    public static String getPathExtension(String path) {
		if (!TextUtils.isEmpty(path)) {
			int i = path.lastIndexOf('.');
			if (i > 0 && i < path.length() - 1) {
				return path.substring(i + 1).toLowerCase();
			}
		}
		return "";
	}
    
    public void DumpToFile() {
    	if (mFrameNumber++ != 10) {
    		return;
    	}
    	//gl.glReadPixels(0, 0, mOutputWidth, mOutputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bufferImg);
    	GLES20.glReadPixels(mStartX, mStartY, mWidth, mHeight,
                            VideoDumpConfig.PIXEL_FORMAT,
                            VideoDumpConfig.PIXEL_TYPE,
                            mBuffer);
        //checkGlError("glReadPixels");

//        Log.d(TAG, mDrawNumber + "/" + frameNumber + " after  glReadPixels "
//              + System.currentTimeMillis());
    	
    	 String filename =  VideoDumpConfig.ROOT_DIR + VideoDumpConfig.IMAGE_PREFIX
                 + mFrameNumber + VideoDumpConfig.IMAGE_SUFFIX;
    	 
    	Bitmap img = MyBitmap.createMyBitmap(mBuffer.array(), mWidth, mHeight, 90);
    	
    	if (img == null) {
    		return;
    	}
    	
		boolean createBitmapSuccess = false;
		if (getPathExtension(filename).equalsIgnoreCase("jpeg") || getPathExtension(filename).equalsIgnoreCase("jpg"))
			createBitmapSuccess = MyBitmap.compressBitmap(img, Bitmap.CompressFormat.JPEG, filename, 100);
		else
			createBitmapSuccess = MyBitmap.compressBitmap(img, Bitmap.CompressFormat.PNG, filename, 100);
		
		Log.i("mTakePictures", "compressBitmap:" + createBitmapSuccess);
		if (img != null && !img.isRecycled()) {
			img.recycle();
			img = null;
		}
		System.gc();

//        try {
//            mImageListWriter.write(filename);
//            mImageListWriter.newLine();
//            FileOutputStream fos = new FileOutputStream(filename);
//            fos.write(mBuffer.array());
//            fos.close();
//        } catch (java.io.IOException e) {
//            //Log.e(TAG, e.getMessage(), e);
//        }
    }
}
