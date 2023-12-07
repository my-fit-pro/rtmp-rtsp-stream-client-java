/*
 * Copyright (C) 2023 pedroSG94.
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

package com.pedro.library.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.input.gl.FilterAction;
import com.pedro.encoder.input.gl.render.ManagerRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.gl.AspectRatioMode;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.library.R;
import com.pedro.library.util.Filter;

/**
 * Created by pedro on 9/09/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OpenGlView extends OpenGlViewBase {

  private final ManagerRender managerRender = new ManagerRender();
  private AspectRatioMode aspectRatioMode = AspectRatioMode.Adjust;

  public OpenGlView(Context context) {
    super(context);
  }

  public OpenGlView(Context context, AttributeSet attrs) {
    super(context, attrs);
    TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.OpenGlView);
    try {
      aspectRatioMode = AspectRatioMode.Companion.fromId(typedArray.getInt(R.styleable.OpenGlView_aspectRatioMode, AspectRatioMode.NONE.ordinal()));
      boolean AAEnabled = typedArray.getBoolean(R.styleable.OpenGlView_AAEnabled, false);
      ManagerRender.numFilters = typedArray.getInt(R.styleable.OpenGlView_numFilters, 0);
      boolean isFlipHorizontal = typedArray.getBoolean(R.styleable.OpenGlView_isFlipHorizontal, false);
      boolean isFlipVertical = typedArray.getBoolean(R.styleable.OpenGlView_isFlipVertical, false);
      managerRender.setCameraFlip(isFlipHorizontal, isFlipVertical);
      managerRender.enableAA(AAEnabled);
    } finally {
      typedArray.recycle();
    }
  }

  @Override
  public SurfaceTexture getSurfaceTexture() {
    return managerRender.getSurfaceTexture();
  }

  @Override
  public Surface getSurface() {
    return managerRender.getSurface();
  }

  @Override
  public void setFilter(int filterPosition, BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.SET_INDEX, filterPosition, baseFilterRender));
  }

  @Override
  public void addFilter(BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.ADD, 0, baseFilterRender));
  }

  @Override
  public void addFilter(int filterPosition, BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.ADD_INDEX, filterPosition, baseFilterRender));
  }

  @Override
  public void clearFilters() {
    filterQueue.add(new Filter(FilterAction.CLEAR, 0, null));
  }

  @Override
  public void removeFilter(int filterPosition) {
    filterQueue.add(new Filter(FilterAction.REMOVE_INDEX, filterPosition, null));
  }

  @Override
  public void removeFilter(BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.REMOVE, 0, baseFilterRender));
  }

  @Override
  public int filtersCount() {
    return managerRender.filtersCount();
  }

  @Override
  public void setFilter(BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(FilterAction.SET, 0, baseFilterRender));
  }

  @Override
  public void enableAA(boolean AAEnabled) {
    managerRender.enableAA(AAEnabled);
  }

  @Override
  public void setRotation(int rotation) {
    managerRender.setCameraRotation(rotation);
  }

  public void setAspectRatioMode(AspectRatioMode aspectRatioMode) {
    this.aspectRatioMode = aspectRatioMode;
  }

  public void setCameraFlip(boolean isFlipHorizontal, boolean isFlipVertical) {
    managerRender.setCameraFlip(isFlipHorizontal, isFlipVertical);
  }

  @Override
  public boolean isAAEnabled() {
    return managerRender != null && managerRender.isAAEnabled();
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    Log.i(TAG, "size: " + width + "x" + height);
    this.previewWidth = width;
    this.previewHeight = height;
    if (managerRender != null) managerRender.setPreviewSize(previewWidth, previewHeight);
  }

  @Override
  public void run() {
    surfaceManager.release();
    surfaceManager.eglSetup(getHolder().getSurface());
    surfaceManager.makeCurrent();
    managerRender.initGl(getContext(), encoderWidth, encoderHeight, previewWidth, previewHeight);
    managerRender.getSurfaceTexture().setOnFrameAvailableListener(this);
    surfaceManagerPhoto.release();
    surfaceManagerPhoto.eglSetup(encoderWidth, encoderHeight, surfaceManager);
    semaphore.release();
    try {
      while (running) {
        fpsLimiter.setFrameStartTs();
        if (frameAvailable || forceRender) {
          frameAvailable = false;
          surfaceManager.makeCurrent();
          managerRender.updateFrame();
          managerRender.drawOffScreen();
          managerRender.drawScreen(previewWidth, previewHeight, aspectRatioMode, 0,
              isPreviewVerticalFlip, isPreviewHorizontalFlip);
          surfaceManager.swapBuffer();

          if (!filterQueue.isEmpty()) {
            Filter filter = filterQueue.take();
            managerRender.setFilterAction(filter.getFilterAction(), filter.getPosition(), filter.getBaseFilterRender());
          }

          synchronized (sync) {
            if (surfaceManagerEncoder.isReady() && !fpsLimiter.limitFPS()) {
              int w = muteVideo ? 0 : encoderWidth;
              int h = muteVideo ? 0 : encoderHeight;
              surfaceManagerEncoder.makeCurrent();
              managerRender.drawScreen(w, h, aspectRatioMode,
                  streamRotation, isStreamVerticalFlip, isStreamHorizontalFlip);
              surfaceManagerEncoder.swapBuffer();
            }
            if (takePhotoCallback != null && surfaceManagerPhoto.isReady()) {
              surfaceManagerPhoto.makeCurrent();
              managerRender.drawScreen(encoderWidth, encoderHeight, aspectRatioMode,
                  streamRotation, isStreamVerticalFlip, isStreamHorizontalFlip);
              takePhotoCallback.onTakePhoto(GlUtil.getBitmap(encoderWidth, encoderHeight));
              takePhotoCallback = null;
              surfaceManagerPhoto.swapBuffer();
            }
          }
        }
        synchronized (sync) {
          long sleep = fpsLimiter.getSleepTime();
          if (sleep > 0) sync.wait(sleep);
        }
      }
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
    } finally {
      managerRender.release();
      surfaceManagerPhoto.release();
      surfaceManagerEncoder.release();
      surfaceManager.release();
    }
  }
}