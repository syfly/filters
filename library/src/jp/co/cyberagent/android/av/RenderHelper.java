package jp.co.cyberagent.android.av;
import java.io.File;
import java.io.IOException;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;











//import com.android.grafika.TextureMovieEncoder2;
//import com.android.grafika.VideoEncoderCore;
//import com.android.grafika.RecordFBOActivity.ActivityHandler;
//import com.android.grafika.RecordFBOActivity.RenderHandler;
import com.android.grafika.gles.Drawable2d;
import com.android.grafika.gles.EglCore;
import com.android.grafika.gles.FlatShadedProgram;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.GlUtil;
import com.android.grafika.gles.Sprite2d;
import com.android.grafika.gles.Texture2dProgram;
import com.android.grafika.gles.WindowSurface;

public class RenderHelper extends Thread {
        // Object must be created on render thread to get correct Looper, but is used from
        // UI thread, so we need to declare it volatile to ensure the UI thread sees a fully

		private static final String TAG = "";
        // Used to wait for the thread to start.

        private volatile SurfaceHolder mSurfaceHolder;  // may be updated by UI thread
        private EglCore mEglCore;
        private WindowSurface mWindowSurface;
        private FlatShadedProgram mProgram;
        
        // Orthographic projection matrix.
        private float[] mDisplayProjectionMatrix = new float[16];

        private final Drawable2d mTriDrawable = new Drawable2d(Drawable2d.Prefab.TRIANGLE);
        private final Drawable2d mRectDrawable = new Drawable2d(Drawable2d.Prefab.RECTANGLE);

        // One spinning triangle, one bouncing rectangle, and four edge-boxes.
        private Sprite2d mTri;
        private Sprite2d mRect;
        private Sprite2d mEdges[];
        private Sprite2d mRecordRect;
        private float mRectVelX, mRectVelY;     // velocity, in viewport units per second
        private float mInnerLeft, mInnerTop, mInnerRight, mInnerBottom;

        public final float[] mIdentityMatrix;
        
        // Previous frame time.
        private long mPrevTimeNanos;

        // Used for off-screen rendering.
        public int mOffscreenTexture;
        public int mFramebuffer;
        private int mDepthBuffer;
        public FullFrameRect mFullScreen;
        
        // Used for recording.
        private File mOutputFile;
        public WindowSurface mInputWindowSurface;
        public TextureMovieEncoder2 mVideoEncoder;
        public Rect mVideoRect;
		private Texture2dProgram mTexProgram;

        /**
         * Pass in the SurfaceView's SurfaceHolder.  Note the Surface may not yet exist.
         */
        public RenderHelper(SurfaceHolder holder, File outputFile, long refreshPeriodNs) {
            mSurfaceHolder = holder;
            mOutputFile = outputFile;

            mVideoRect = new Rect();

            mIdentityMatrix = new float[16];
            Matrix.setIdentityM(mIdentityMatrix, 0);

            mTri = new Sprite2d(mTriDrawable);
            mRect = new Sprite2d(mRectDrawable);
            mEdges = new Sprite2d[4];
            for (int i = 0; i < mEdges.length; i++) {
                mEdges[i] = new Sprite2d(mRectDrawable);
            }
            mRecordRect = new Sprite2d(mRectDrawable);
            
            mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE | EglCore.FLAG_TRY_GLES3);
        }

        private void prepareGl() {
            Log.d(TAG, "prepareGl");

            //mWindowSurface = new WindowSurface(mEglCore, surface, false);
            //mWindowSurface.makeCurrent();

            // Used for blitting texture to FBO.
            mFullScreen = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));

            // Program used for drawing onto the screen.
            mProgram = new FlatShadedProgram();

            // Set the background color.
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

            // Disable depth testing -- we're 2D only.
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);

            // Don't need backface culling.  (If you're feeling pedantic, you can turn it on to
            // make sure we're defining our shapes correctly.)
            GLES20.glDisable(GLES20.GL_CULL_FACE);

            //mActivityHandler.sendGlesVersion(mEglCore.getGlVersion());
        }
        
        /**
         * Shuts everything down.
         */
        public void shutdown() {
            Log.d(TAG, "shutdown");
            stopEncoder();
        }

        public int getsurfaceTex() {
            //Surface surface = holder.getSurface();
            //mWindowSurface = new WindowSurface(mEglCore, surface, false);
            //mWindowSurface.makeCurrent();

            // Create and configure the SurfaceTexture, which will receive frames from the
            // camera.  We set the textured rect's program to render from it.
            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
            int textureId = mTexProgram.createTextureObject();
            //SurfaceTexture mCameraTexture = new SurfaceTexture(textureId);
            mRect.setTexture(textureId);


            //mCameraTexture.setOnFrameAvailableListener(this);
            
            return textureId;
        }
        
        /**
         * Prepares the surface.
         */
        public void surfaceCreated() {
            //Surface surface = mSurfaceHolder.getSurface();
            
            prepareGl();
        }


        /**
         * Handles changes to the size of the underlying surface.  Adjusts viewport as needed.
         * Must be called before we start drawing.
         * (Called from RenderHandler.)
         */
        public void surfaceChanged(int width, int height) {
            Log.d(TAG, "surfaceChanged " + width + "x" + height);
            
            
            prepareFramebuffer(width, height);

            // Use full window.
            GLES20.glViewport(0, 0, width, height);

            // Simple orthographic projection, with (0,0) in lower-left corner.
            Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, width, 0, height, -1, 1);

            //int smallDim = Math.min(width, height);

            // Set initial shape size / position / velocity based on window size.  Movement
            // has the same "feel" on all devices, but the actual path will vary depending
            // on the screen proportions.  We do it here, rather than defining fixed values
            // and tweaking the projection matrix, so that our squares are square.
//            mTri.setColor(0.1f, 0.9f, 0.1f);
//            mTri.setScale(smallDim / 4.0f, smallDim / 4.0f);
//            mTri.setPosition(width / 2.0f, height / 2.0f);
//            mRect.setColor(0.9f, 0.1f, 0.1f);
//            mRect.setScale(smallDim / 8.0f, smallDim / 8.0f);
//            mRect.setPosition(width / 2.0f, height / 2.0f);
//            mRectVelX = 1 + smallDim / 4.0f;
//            mRectVelY = 1 + smallDim / 5.0f;

            // left edge
//            float edgeWidth = 1 + width / 64.0f;
//            mEdges[0].setScale(edgeWidth, height);
//            mEdges[0].setPosition(edgeWidth / 2.0f, height / 2.0f);
//            // right edge
//            mEdges[1].setScale(edgeWidth, height);
//            mEdges[1].setPosition(width - edgeWidth / 2.0f, height / 2.0f);
//            // top edge
//            mEdges[2].setScale(width, edgeWidth);
//            mEdges[2].setPosition(width / 2.0f, height - edgeWidth / 2.0f);
//            // bottom edge
//            mEdges[3].setScale(width, edgeWidth);
//            mEdges[3].setPosition(width / 2.0f, edgeWidth / 2.0f);
//
//            mRecordRect.setColor(1.0f, 1.0f, 1.0f);
//            mRecordRect.setScale(edgeWidth * 2f, edgeWidth * 2f);
//            mRecordRect.setPosition(edgeWidth / 2.0f, edgeWidth / 2.0f);

            // Inner bounding rect, used to bounce objects off the walls.
//            mInnerLeft = mInnerBottom = edgeWidth;
//            mInnerRight = width - 1 - edgeWidth;
//            mInnerTop = height - 1 - edgeWidth;

//            Log.d(TAG, "mTri: " + mTri);
//            Log.d(TAG, "mRect: " + mRect);
        }

        /**
         * Prepares the off-screen framebuffer.
         */
        private void prepareFramebuffer(int width, int height) {
            GlUtil.checkGlError("prepareFramebuffer start");

            int[] values = new int[1];

            // Create a texture object and bind it.  This will be the color buffer.
            GLES20.glGenTextures(1, values, 0);
            GlUtil.checkGlError("glGenTextures");
            mOffscreenTexture = values[0];   // expected > 0
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOffscreenTexture);
            GlUtil.checkGlError("glBindTexture " + mOffscreenTexture);

            // Create texture storage.
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

            // Set parameters.  We're probably using non-power-of-two dimensions, so
            // some values may not be available for use.
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            GlUtil.checkGlError("glTexParameter");

            // Create framebuffer object and bind it.
            GLES20.glGenFramebuffers(1, values, 0);
            GlUtil.checkGlError("glGenFramebuffers");
            mFramebuffer = values[0];    // expected > 0
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer);
            GlUtil.checkGlError("glBindFramebuffer " + mFramebuffer);

            // Create a depth buffer and bind it.
            GLES20.glGenRenderbuffers(1, values, 0);
            GlUtil.checkGlError("glGenRenderbuffers");
            mDepthBuffer = values[0];    // expected > 0
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mDepthBuffer);
            GlUtil.checkGlError("glBindRenderbuffer " + mDepthBuffer);

            // Allocate storage for the depth buffer.
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height);
            GlUtil.checkGlError("glRenderbufferStorage");

            // Attach the depth buffer and the texture (color buffer) to the framebuffer object.
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, mDepthBuffer);
            GlUtil.checkGlError("glFramebufferRenderbuffer");
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mOffscreenTexture, 0);
            GlUtil.checkGlError("glFramebufferTexture2D");

            // See if GLES is happy with all this.
            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("Framebuffer not complete, status=" + status);
            }

            // Switch back to the default framebuffer.
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

            GlUtil.checkGlError("prepareFramebuffer done");
        }

        /**
         * Releases most of the GL resources we currently hold.
         * <p>
         * Does not release EglCore.
         */
        public void releaseGl() {
            GlUtil.checkGlError("releaseGl start");

            int[] values = new int[1];

//            if (mWindowSurface != null) {
//                mWindowSurface.release();
//                mWindowSurface = null;
//            }
            if (mProgram != null) {
                mProgram.release();
                mProgram = null;
            }
            if (mOffscreenTexture > 0) {
                values[0] = mOffscreenTexture;
                GLES20.glDeleteTextures(1, values, 0);
                mOffscreenTexture = -1;
            }
            if (mFramebuffer > 0) {
                values[0] = mFramebuffer;
                GLES20.glDeleteFramebuffers(1, values, 0);
                mFramebuffer = -1;
            }
            if (mDepthBuffer > 0) {
                values[0] = mDepthBuffer;
                GLES20.glDeleteRenderbuffers(1, values, 0);
                mDepthBuffer = -1;
            }
            if (mFullScreen != null) {
                mFullScreen.release(false); // TODO: should be "true"; must ensure mEglCore current
                mFullScreen = null;
            }

            GlUtil.checkGlError("releaseGl done");

            mEglCore.makeNothingCurrent();
        }

        /**
         * Creates the video encoder object and starts the encoder thread.  Creates an EGL
         * surface for encoder input.
         */
        public void startEncoder(int windowWidth, int windowHeight) {
            Log.d(TAG, "starting to record");
            // Record at 1280x720, regardless of the window dimensions.  The encoder may
            // explode if given "strange" dimensions, e.g. a width that is not a multiple
            // of 16.  We can box it as needed to preserve dimensions.
            final int BIT_RATE = 4000000;   // 4Mbps
            final int VIDEO_WIDTH = 1280;
            final int VIDEO_HEIGHT = 720;
//            int windowWidth = mWindowSurface.getWidth();
//            int windowHeight = mWindowSurface.getHeight();
            float windowAspect = (float) windowHeight / (float) windowWidth;
            int outWidth, outHeight;
            if (VIDEO_HEIGHT > VIDEO_WIDTH * windowAspect) {
                // limited by narrow width; reduce height
                outWidth = VIDEO_WIDTH;
                outHeight = (int) (VIDEO_WIDTH * windowAspect);
            } else {
                // limited by short height; restrict width
                outHeight = VIDEO_HEIGHT;
                outWidth = (int) (VIDEO_HEIGHT / windowAspect);
            }
            int offX = (VIDEO_WIDTH - outWidth) / 2;
            int offY = (VIDEO_HEIGHT - outHeight) / 2;
            mVideoRect.set(offX, offY, offX + outWidth, offY + outHeight);
            Log.d(TAG, "Adjusting window " + windowWidth + "x" + windowHeight +
                    " to +" + offX + ",+" + offY + " " +
                    mVideoRect.width() + "x" + mVideoRect.height());

            VideoEncoderCore encoderCore;
            try {
                encoderCore = new VideoEncoderCore(VIDEO_WIDTH, VIDEO_HEIGHT,
                        BIT_RATE, mOutputFile);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            mInputWindowSurface = new WindowSurface(mEglCore, encoderCore.getInputSurface(), true);
            mVideoEncoder = new TextureMovieEncoder2(encoderCore);
        }

        public void drain() {
        	mInputWindowSurface.makeCurrent();
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);    // again, only really need to
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);     //  clear pixels outside rect
            GLES20.glViewport(mVideoRect.left, mVideoRect.top,
                    mVideoRect.width(), mVideoRect.height());
            //mFullScreen.drawFrame(mOffscreenTexture, mIdentityMatrix);
            //mInputWindowSurface.setPresentationTime(1);
            //mInputWindowSurface.swapBuffers();
        }
        /**
         * Stops the video encoder if it's running.
         */
        public void stopEncoder() {
            if (mVideoEncoder != null) {
                Log.d(TAG, "stopping recorder, mVideoEncoder=" + mVideoEncoder);
                mVideoEncoder.stopRecording();
                // TODO: wait (briefly) until it finishes shutting down so we know file is
                //       complete, or have a callback that updates the UI
                mVideoEncoder = null;
            }
            if (mInputWindowSurface != null) {
                mInputWindowSurface.release();
                mInputWindowSurface = null;
            }
        }
        /**
         * We use the time delta from the previous event to determine how far everything
         * moves.  Ideally this will yield identical animation sequences regardless of
         * the device's actual refresh rate.
         */
        public void update(long timeStampNanos) {
            // Compute time from previous frame.
            long intervalNanos;
            if (mPrevTimeNanos == 0) {
                intervalNanos = 0;
            } else {
                intervalNanos = timeStampNanos - mPrevTimeNanos;

                final long ONE_SECOND_NANOS = 1000000000L;
                if (intervalNanos > ONE_SECOND_NANOS) {
                    // A gap this big should only happen if something paused us.  We can
                    // either cap the delta at one second, or just pretend like this is
                    // the first frame and not advance at all.
                    Log.d(TAG, "Time delta too large: " +
                            (double) intervalNanos / ONE_SECOND_NANOS + " sec");
                    intervalNanos = 0;
                }
            }
            mPrevTimeNanos = timeStampNanos;

            final float ONE_BILLION_F = 1000000000.0f;
            final float elapsedSeconds = intervalNanos / ONE_BILLION_F;

            // Spin the triangle.  We want one full 360-degree rotation every 3 seconds,
            // or 120 degrees per second.
            final int SECS_PER_SPIN = 3;
            float angleDelta = (360.0f / SECS_PER_SPIN) * elapsedSeconds;
            mTri.setRotation(mTri.getRotation() + angleDelta);

            // Bounce the rect around the screen.  The rect is a 1x1 square scaled up to NxN.
            // We don't do fancy collision detection, so it's possible for the box to slightly
            // overlap the edges.  We draw the edges last, so it's not noticeable.
            float xpos = mRect.getPositionX();
            float ypos = mRect.getPositionY();
            float xscale = mRect.getScaleX();
            float yscale = mRect.getScaleY();
            xpos += mRectVelX * elapsedSeconds;
            ypos += mRectVelY * elapsedSeconds;
            if ((mRectVelX < 0 && xpos - xscale/2 < mInnerLeft) ||
                    (mRectVelX > 0 && xpos + xscale/2 > mInnerRight+1)) {
                mRectVelX = -mRectVelX;
            }
            if ((mRectVelY < 0 && ypos - yscale/2 < mInnerBottom) ||
                    (mRectVelY > 0 && ypos + yscale/2 > mInnerTop+1)) {
                mRectVelY = -mRectVelY;
            }
            mRect.setPosition(xpos, ypos);
        }
        
        /**
         * Draws the scene.
         */
        public void draw() {
            GlUtil.checkGlError("draw start");

            // Clear to a non-black color to make the content easily differentiable from
            // the pillar-/letter-boxing.
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mRect.draw(mTexProgram, mDisplayProjectionMatrix);

//            mTri.draw(mProgram, mDisplayProjectionMatrix);
//            mRect.draw(mProgram, mDisplayProjectionMatrix);
//            for (int i = 0; i < 4; i++) {
//                if (false && mPreviousWasDropped) {
//                    mEdges[i].setColor(1.0f, 0.0f, 0.0f);
//                } else {
//                    mEdges[i].setColor(0.5f, 0.5f, 0.5f);
//                }
//                mEdges[i].draw(mProgram, mDisplayProjectionMatrix);
//            }
            
            //mRecordRect.setColor(0.0f, 1.0f, 0.0f);
            
            // Give a visual indication of the recording method.
//            switch (mRecordMethod) {
//                case RECMETHOD_DRAW_TWICE:
//                    mRecordRect.setColor(1.0f, 0.0f, 0.0f);
//                    break;
//                case RECMETHOD_FBO:
//                    mRecordRect.setColor(0.0f, 1.0f, 0.0f);
//                    break;
//                case RECMETHOD_BLIT_FRAMEBUFFER:
//                    mRecordRect.setColor(0.0f, 0.0f, 1.0f);
//                    break;
//                default:
//            }
            //mRecordRect.draw(mProgram, mDisplayProjectionMatrix);

            GlUtil.checkGlError("draw done");
        }
}