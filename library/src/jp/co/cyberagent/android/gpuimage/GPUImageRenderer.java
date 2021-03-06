/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.cyberagent.android.gpuimage;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
//import android.opengl.GLSurfaceView.Renderer;
import android.util.Log;
import jp.co.cyberagent.android.av.RenderHelper;
import jp.co.cyberagent.android.gpuimage.GLSurfaceView.Renderer;
import jp.co.cyberagent.android.gpuimage.filters.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.util.TextureRotationUtil;
import jp.co.cyberagent.android.gpuimage.util.VideoDump;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.android.grafika.gles.GlUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.Queue;

import static jp.co.cyberagent.android.gpuimage.util.TextureRotationUtil.TEXTURE_NO_ROTATION;

@SuppressLint("WrongCall")
@TargetApi(11)
public class GPUImageRenderer implements Renderer, PreviewCallback {
    public static final int NO_IMAGE = -1;
    public static String TAG = "GPUImageRenderer";
    static final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
    };

    private GPUImageFilter mFilter;

    public final Object mSurfaceChangedWaiter = new Object();

    private int mGLTextureId = NO_IMAGE;
    private SurfaceTexture mSurfaceTexture = null;
    private final FloatBuffer mGLCubeBuffer;
    private final FloatBuffer mGLTextureBuffer;
    private IntBuffer mGLRgbBuffer;

    private int mOutputWidth;
    private int mOutputHeight;
    private int mImageWidth;
    private int mImageHeight;
    private int mAddedPadding;

    private final Queue<Runnable> mRunOnDraw;
    private Rotation mRotation;
    private boolean mFlipHorizontal;
    private boolean mFlipVertical;
    private GPUImage.ScaleType mScaleType = GPUImage.ScaleType.CENTER_CROP;
	private VideoDump mVideoDump;
	private RenderHelper mRenderHelper = null;
	private int mWidth = -1;
	private int mHeight = -1;
	private boolean mFrameAvailable;

    public GPUImageRenderer(final GPUImageFilter filter) {
        mFilter = filter;
        mRunOnDraw = new LinkedList<Runnable>();

        mGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(CUBE).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mVideoDump = new VideoDump();
        setRotation(Rotation.NORMAL, false, false);
        
        mRenderHelper = new RenderHelper(null, new File("/sdcard/output.mp4"), 1);
    }

    public void setRenderHelper() {
    	mRenderHelper.startEncoder(mWidth, mHeight);
//    	if (mRenderHelper == null) {
//    		mRenderHelper = new RenderHelper(null, new File("/sdcard/output.mp4"), 1);
//    		//renderHelper.startEncoder(mWidth, mHeight);
//    	}
    }
    
    public void closeRenderHelper() {
    	if (mRenderHelper != null) {
    		mRenderHelper.stopEncoder();
    	}
    }
    
    @Override
    public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        mFilter.init();
        
        if (mRenderHelper != null) {
        	mRenderHelper.surfaceCreated();
        }
    }

    @Override
    public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
        mOutputWidth = width;
        mOutputHeight = height;
        GLES20.glViewport(0, 0, width, height);
        
        mVideoDump.init(width, height);
        mWidth = width;
        mHeight = height;
        GLES20.glUseProgram(mFilter.getProgram());
        mFilter.onOutputSizeChanged(width, height);
        
        if (mRenderHelper != null) {
        	Log.d(TAG, "onDrawFrame surfaceChanged");
        	//mRenderHelper.startEncoder(width, height);
        	mRenderHelper.surfaceChanged(width, height);
        }
        
        synchronized (mSurfaceChangedWaiter) {
            mSurfaceChangedWaiter.notifyAll();
        }
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
    	//Log.d(TAG, "onDrawFrame mGLTextureId=" + mGLTextureId);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        synchronized (mRunOnDraw) {
            while (!mRunOnDraw.isEmpty()) {
                mRunOnDraw.poll().run();
            }
        }
        mFilter.onDraw(mGLTextureId, mGLCubeBuffer, mGLTextureBuffer);
        if (mSurfaceTexture != null) {
            mSurfaceTexture.updateTexImage();
            
            mVideoDump.DumpToFile();
        }

        
        Log.d(TAG, "onDrawFrame end mGLTextureId" + mGLTextureId);
    }

    @Override
    public void onDrawFrame(GL10 gl, GLSurfaceView view) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        synchronized (mRunOnDraw) {
            while (!mRunOnDraw.isEmpty()) {
                mRunOnDraw.poll().run();
            }
        }

        
        
//        if (mFrameAvailable) {
//        	if (mSurfaceTexture != null) {
//                mSurfaceTexture.updateTexImage();
//                
//                //mVideoDump.DumpToFile();
//            }
//        	mFrameAvailable = false;
//        }
        mSurfaceTexture.updateTexImage();
//        float[] mtx = new float[16];
//        mSurfaceTexture.getTransformMatrix(mtx);
//        mFilter.onDrawEx(mGLTextureId, mGLCubeBuffer, mGLTextureBuffer);
//        view.eglSwap();
        if (mRenderHelper != null) {
	        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mRenderHelper.mFramebuffer);
	        mRenderHelper.draw();
	        GlUtil.checkGlError("glBindFramebuffer");
	        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
	        
	        mRenderHelper.mFullScreen.drawFrame(mRenderHelper.mOffscreenTexture, mRenderHelper.mIdentityMatrix);
	        //mFilter.onDraw(mRenderHelper.mOffscreenTexture, mGLCubeBuffer, mGLTextureBuffer);
	        view.eglSwap();

	        
	        // Blit to encoder.
	        if (mRenderHelper.mVideoEncoder != null) {
	        	 mRenderHelper.mVideoEncoder.frameAvailableSoon();
	             mRenderHelper.mInputWindowSurface.makeCurrent();
	             GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);    // again, only really need to
	             GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);     //  clear pixels outside rect
	             GLES20.glViewport(mRenderHelper.mVideoRect.left, mRenderHelper.mVideoRect.top,
	             		mRenderHelper.mVideoRect.width(), mRenderHelper.mVideoRect.height());
	             //mRenderHelper.mFullScreen.drawFrame(mOffscreenTexture, mIdentityMatrix);
	             mFilter.onDraw(mRenderHelper.mOffscreenTexture, mGLCubeBuffer, mGLTextureBuffer);
	             mRenderHelper.mInputWindowSurface.setPresentationTime(0);
	             mRenderHelper.mInputWindowSurface.swapBuffers();

	             // Restore previous values.
	             GLES20.glViewport(0, 0, -1, -1);
	             
	             
	             view.eglMakeCurrent();
	        }
	        
	        Log.d(TAG, "onDrawFrame end mGLTextureId" + mRenderHelper.mOffscreenTexture);
        }
     
        
    };
    
    @Override
    public void onPreviewFrame(final byte[] data, final Camera camera) {
    	//Log.d(TAG, "onPreviewFrame mGLTextureId=" + mGLTextureId);
    	
//        final Size previewSize = camera.getParameters().getPreviewSize();
//        if (mGLRgbBuffer == null) {
//            mGLRgbBuffer = IntBuffer.allocate(previewSize.width * previewSize.height);
//        }
//        if (mRunOnDraw.isEmpty()) {
//            runOnDraw(new Runnable() {
//                @Override
//                public void run() {
//                	Log.d(TAG, "onPreviewFrame Run mGLTextureId=" + mGLTextureId);
//                    GPUImageNativeLibrary.YUVtoRBGA(data, previewSize.width, previewSize.height,
//                            mGLRgbBuffer.array());
//                    mGLTextureId = OpenGlUtils.loadTexture(mGLRgbBuffer, previewSize, mGLTextureId);
//                    camera.addCallbackBuffer(data);
//
//                    if (mImageWidth != previewSize.width) {
//                        mImageWidth = previewSize.width;
//                        mImageHeight = previewSize.height;
//                        adjustImageScaling();
//                    }
//                }
//            });
//        }
    }
    
    public void setUpSurfaceTexture(final Camera camera) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
            	Log.d(TAG, "setUpSurfaceTexture Run");
                //int[] textures = new int[1];
            	mGLTextureId = mRenderHelper.getsurfaceTex();
                //GLES20.glGenTextures(1, mGLTextureId, 0);
                mSurfaceTexture = new SurfaceTexture(mGLTextureId);
                mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
					
					@Override
					public void onFrameAvailable(SurfaceTexture surfaceTexture) {
						// TODO Auto-generated method stub
						mFrameAvailable = true;
						
						Log.d(TAG, "setUpSurfaceTexture Run frameAvailable");
					}
				});
                try {
                    camera.setPreviewTexture(mSurfaceTexture);
                    camera.setPreviewCallback(GPUImageRenderer.this);
                    camera.startPreview();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void setFilter(final GPUImageFilter filter) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                final GPUImageFilter oldFilter = mFilter;
                mFilter = filter;
                if (oldFilter != null) {
                    oldFilter.destroy();
                }
                mFilter.init();
                GLES20.glUseProgram(mFilter.getProgram());
                mFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight);
            }
        });
    }

    public void deleteImage() {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                GLES20.glDeleteTextures(1, new int[]{
                        mGLTextureId
                }, 0);
                mGLTextureId = NO_IMAGE;
            }
        });
    }

    public void setImageBitmap(final Bitmap bitmap) {
        setImageBitmap(bitmap, true);
    }

    public void setImageBitmap(final Bitmap bitmap, final boolean recycle) {
        if (bitmap == null) {
            return;
        }

        runOnDraw(new Runnable() {

            @Override
            public void run() {
                Bitmap resizedBitmap = null;
                if (bitmap.getWidth() % 2 == 1) {
                    resizedBitmap = Bitmap.createBitmap(bitmap.getWidth() + 1, bitmap.getHeight(),
                            Bitmap.Config.ARGB_8888);
                    Canvas can = new Canvas(resizedBitmap);
                    can.drawARGB(0x00, 0x00, 0x00, 0x00);
                    can.drawBitmap(bitmap, 0, 0, null);
                    mAddedPadding = 1;
                } else {
                    mAddedPadding = 0;
                }

                mGLTextureId = OpenGlUtils.loadTexture(
                        resizedBitmap != null ? resizedBitmap : bitmap, mGLTextureId, recycle);
                if (resizedBitmap != null) {
                    resizedBitmap.recycle();
                }
                mImageWidth = bitmap.getWidth();
                mImageHeight = bitmap.getHeight();
                adjustImageScaling();
            }
        });
    }

    public void setScaleType(GPUImage.ScaleType scaleType) {
        mScaleType = scaleType;
    }

    protected int getFrameWidth() {
        return mOutputWidth;
    }

    protected int getFrameHeight() {
        return mOutputHeight;
    }

    private void adjustImageScaling() {
        float outputWidth = mOutputWidth;
        float outputHeight = mOutputHeight;
        if (mRotation == Rotation.ROTATION_270 || mRotation == Rotation.ROTATION_90) {
            outputWidth = mOutputHeight;
            outputHeight = mOutputWidth;
        }

        float ratio1 = outputWidth / mImageWidth;
        float ratio2 = outputHeight / mImageHeight;
        float ratioMin = Math.min(ratio1, ratio2);
        mImageWidth = Math.round(mImageWidth * ratioMin);
        mImageHeight = Math.round(mImageHeight * ratioMin);

        float ratioWidth = 1.0f;
        float ratioHeight = 1.0f;
        if (mImageWidth != outputWidth) {
            ratioWidth = mImageWidth / outputWidth;
        } else if (mImageHeight != outputHeight) {
            ratioHeight = mImageHeight / outputHeight;
        }

        float[] cube = CUBE;
        float[] textureCords = TextureRotationUtil.getRotation(mRotation, mFlipHorizontal, mFlipVertical);
        if (mScaleType == GPUImage.ScaleType.CENTER_CROP) {
            float distHorizontal = (1 / ratioWidth - 1) / 2;
            float distVertical = (1 / ratioHeight - 1) / 2;
            textureCords = new float[]{
                    addDistance(textureCords[0], distVertical), addDistance(textureCords[1], distHorizontal),
                    addDistance(textureCords[2], distVertical), addDistance(textureCords[3], distHorizontal),
                    addDistance(textureCords[4], distVertical), addDistance(textureCords[5], distHorizontal),
                    addDistance(textureCords[6], distVertical), addDistance(textureCords[7], distHorizontal),
            };
        } else {
            cube = new float[]{
                    CUBE[0] * ratioWidth, CUBE[1] * ratioHeight,
                    CUBE[2] * ratioWidth, CUBE[3] * ratioHeight,
                    CUBE[4] * ratioWidth, CUBE[5] * ratioHeight,
                    CUBE[6] * ratioWidth, CUBE[7] * ratioHeight,
            };
        }

        mGLCubeBuffer.clear();
        mGLCubeBuffer.put(cube).position(0);
        mGLTextureBuffer.clear();
        mGLTextureBuffer.put(textureCords).position(0);
    }

    private float addDistance(float coordinate, float distance) {
        return coordinate == 0.0f ? distance : 1 - distance;
    }

    public void setRotationCamera(final Rotation rotation, final boolean flipHorizontal,
            final boolean flipVertical) {
        setRotation(rotation, flipVertical, flipHorizontal);
    }

    public void setRotation(final Rotation rotation, final boolean flipHorizontal,
            final boolean flipVertical) {
        mRotation = rotation;
        mFlipHorizontal = flipHorizontal;
        mFlipVertical = flipVertical;
        adjustImageScaling();
    }

    public Rotation getRotation() {
        return mRotation;
    }

    public boolean isFlippedHorizontally() {
        return mFlipHorizontal;
    }

    public boolean isFlippedVertically() {
        return mFlipVertical;
    }

    protected void runOnDraw(final Runnable runnable) {
        synchronized (mRunOnDraw) {
            mRunOnDraw.add(runnable);
        }
    }
}
