/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.squareup.picasso.Utils.KEY_SEPARATOR;

/** A memory cache which uses a least-recently used eviction policy. */
public class LruCache implements Cache {
  final LinkedHashMap<String, Bitmap> map;
  private final int maxSize;

  private int size;
  private int putCount;
  /**
   * 被移除的bitmap计数
   */
  private int evictionCount;
  private int hitCount;
  private int missCount;

  /** Create a cache using an appropriate portion of the available RAM as the maximum size. */
  public LruCache(@NonNull Context context) {
    this(Utils.calculateMemoryCacheSize(context));
  }

  /** Create a cache with a given maximum size in bytes. */
  public LruCache(int maxSize) {
    if (maxSize <= 0) {
      throw new IllegalArgumentException("Max size must be positive.");
    }
    this.maxSize = maxSize;
    this.map = new LinkedHashMap<String, Bitmap>(0, 0.75f, true);
  }

  @Override public Bitmap get(@NonNull String key) {
    if (key == null) {
      throw new NullPointerException("key == null");
    }

    Bitmap mapValue;
    synchronized (this) {
      // 从hashmap中获取key对应的缓存值。
      mapValue = map.get(key);
      if (mapValue != null) {
        // 不为空，缓存命中加1
        hitCount++;
        return mapValue;
      }
      // 若为空缓存未命中加1
      missCount++;
    }

    return null;
  }

  /**
   * 添加图片到内存缓存
   * @param key
   * @param bitmap
     */
  @Override public void set(@NonNull String key, @NonNull Bitmap bitmap) {
    if (key == null || bitmap == null) {
      throw new NullPointerException("key == null || bitmap == null");
    }

    int addedSize = Utils.getBitmapBytes(bitmap);
    if (addedSize > maxSize) {
      return;
    }

    synchronized (this) {
      putCount++;
      size += addedSize;
      // 返回以前同样key存储的value
      Bitmap previous = map.put(key, bitmap);
      if (previous != null) {
        // value不为空需要减去以前value所占内存
        size -= Utils.getBitmapBytes(previous);
      }
    }

    trimToSize(maxSize);
  }

  /**
   * 整理内存占用，发现内存占用size>maxSize，则移除map中第一个元素，并计数，直到size<=maxSize
   * @param maxSize
     */
  private void trimToSize(int maxSize) {
    while (true) {
      String key;
      Bitmap value;
      synchronized (this) {
        if (size < 0 || (map.isEmpty() && size != 0)) {
          throw new IllegalStateException(
              getClass().getName() + ".sizeOf() is reporting inconsistent results!");
        }

        if (size <= maxSize || map.isEmpty()) {
          break;
        }

        Map.Entry<String, Bitmap> toEvict = map.entrySet().iterator().next();
        key = toEvict.getKey();
        value = toEvict.getValue();
        map.remove(key);
        size -= Utils.getBitmapBytes(value);
        evictionCount++;
      }
    }
  }

  /** Clear the cache. */
  public final void evictAll() {
    trimToSize(-1); // -1 will evict 0-sized elements
  }

  @Override public final synchronized int size() {
    return size;
  }

  @Override public final synchronized int maxSize() {
    return maxSize;
  }

  @Override public final synchronized void clear() {
    evictAll();
  }

  @Override public final synchronized void clearKeyUri(String uri) {
    int uriLength = uri.length();
    for (Iterator<Map.Entry<String, Bitmap>> i = map.entrySet().iterator(); i.hasNext();) {
      Map.Entry<String, Bitmap> entry = i.next();
      String key = entry.getKey();
      Bitmap value = entry.getValue();
      int newlineIndex = key.indexOf(KEY_SEPARATOR);
      // 如果对应的url相同，删除各种图片属性的bitmap缓存
      if (newlineIndex == uriLength && key.substring(0, newlineIndex).equals(uri)) {
        i.remove();
        size -= Utils.getBitmapBytes(value);
      }
    }
  }

  /** Returns the number of times {@link #get} returned a value. */
  public final synchronized int hitCount() {
    return hitCount;
  }

  /** Returns the number of times {@link #get} returned {@code null}. */
  public final synchronized int missCount() {
    return missCount;
  }

  /** Returns the number of times {@link #set(String, Bitmap)} was called. */
  public final synchronized int putCount() {
    return putCount;
  }

  /** Returns the number of values that have been evicted. */
  public final synchronized int evictionCount() {
    return evictionCount;
  }
}
