# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES := $(call host-path,$(LOCAL_PATH)/ffmpeg)
LOCAL_CFLAGS := -fexpensive-optimizations -ftree-vectorize -fomit-frame-pointer -O4 -ffast-math -mfloat-abi=softfp -g

LOCAL_MODULE    := lw-player
LOCAL_SRC_FILES := lw-player.c
LOCAL_LDLIBS += -L$(call host-path,$(LOCAL_PATH)) -lavformat -lavcodec -lavdevice -lavutil -llog \

include $(BUILD_SHARED_LIBRARY)
