#!/bin/bash

ANDROID_COMPILE_SDK="29"
ANDROID_BUILD_TOOLS="29.0.0"
ANDROID_SDK_TOOLS="4333796"
export ARCH=`uname -m`
export ANDROID_NDK_HOME=$HOME/.android/android-ndk-r12b
export ANDROID_HOME=$HOME/.android/tools
export PATH=${ANDROID_NDK_HOME}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools:${PATH}

if [ ! -d "$ANDROID_HOME" ]; then
    mkdir -p $ANDROID_HOME
    pushd $HOME/.android
    wget -q https://dl.google.com/android/repository/sdk-tools-linux-${ANDROID_SDK_TOOLS}.zip
    unzip -q sdk-tools-linux-${ANDROID_SDK_TOOLS}.zip
    popd
fi

if [ ! -d "$ANDROID_NDK_HOME" ]; then
    mkdir -p $ANDROID_NDK_HOME
    pushd $HOME/.android
    wget -q http://dl.google.com/android/repository/android-ndk-r12b-linux-${ARCH}.zip
    unzip -q android-ndk-r12b-linux-${ARCH}.zip
    popd
fi


( sleep 5 && while [ 1   ]; do sleep 1; echo y; done ) | android update sdk --filter tools,platform-tools,build-tools-${ANDROID_BUILD_TOOLS},android-${ANDROID_COMPILE_SDK},extra-google-m2repository --no-ui -a
( sleep 5 && while [ 1 ]; do sleep 1; echo y; done ) | android update sdk --filter extra-android-m2repository --no-ui -a
