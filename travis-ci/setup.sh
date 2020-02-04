#!/bin/bash

ANDROID_COMPILE_SDK="29"
ANDROID_BUILD_TOOLS="29.0.0"
ANDROID_SDK_TOOLS="4333796"
export ARCH=`uname -m`
export ANDROID_NDK_HOME=$HOME/.android/android-ndk-r12b
export ANDROID_HOME=$HOME/.android
export PATH=${ANDROID_NDK_HOME}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools:${PATH}

if [ ! -d "$ANDROID_HOME" ]; then
    mkdir -p $ANDROID_HOME
    pushd $HOME/.android
    wget -q https://dl.google.com/android/repository/sdk-tools-linux-4333796.zip
    unzip -q sdk-tools-linux-4333796.zip
    popd
fi

if [ ! -d "$ANDROID_NDK_HOME" ]; then
    mkdir -p $ANDROID_NDK_HOME
    pushd $HOME/.android
    wget -q http://dl.google.com/android/repository/android-ndk-r12b-linux-${ARCH}.zip
    unzip -q android-ndk-r12b-linux-${ARCH}.zip
    popd
fi

INDEX=0
( sleep 5 && while [ $INDEX -lt 10 ]; do sleep 1; INDEX=$((INDEX + 1)); echo y; done ) | android update sdk --filter tools,platform-tools,build-tools-${ANDROID_BUILD_TOOLS},android-${ANDROID_COMPILE_SDK},extra-google-m2repository --no-ui -a
INDEX=0
( sleep 5 && while [ $INDEX -lt 10 ]; do sleep 1; INDEX=$((INDEX + 1)); echo y; done ) | android update sdk --filter extra-android-m2repository --no-ui -a

cp local.properties.travis local.properties
git submodule update --init
sbt native-build android:package-release
