#!/bin/bash

ANDROID_COMPILE_SDK="29"
ANDROID_BUILD_TOOLS="29.0.0"
ANDROID_SDK_TOOLS="4333796"
export ARCH=`uname -m`
export ANDROID_NDK_HOME=$HOME/.android/android-ndk-r12b
export ANDROID_HOME=$HOME/.android
export ANDROID_CACHE_DIR=$HOME/.android-cache
export PATH=${ANDROID_NDK_HOME}:${ANDROID_HOME}/tools:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/platform-tools:${PATH}

if [ ! -d "$ANDROID_HOME" ]; then
    mkdir -p $ANDROID_HOME
    pushd $HOME/.android
    wget -q https://dl.google.com/android/repository/sdk-tools-linux-4333796.zip
    unzip -q sdk-tools-linux-4333796.zip
    popd
fi

if [ ! -d "$ANDROID_NDK_HOME" ]; then
    mkdir -p $ANDROID_NDK_HOME $ANDROID_CACHE_DIR
    pushd $HOME/.android
    CAHCE_NDK_ZIP=$ANDROID_CACHE_DIR/android-ndk-r12b-linux-${ARCH}.zip
    if [ ! -f  "$CAHCE_NDK_ZIP"]; then
        wget -q -O "$CAHCE_NDK_ZIP" http://dl.google.com/android/repository/android-ndk-r12b-linux-${ARCH}.zip
    fi
    cp "$CAHCE_NDK_ZIP" ./android-ndk-r12b-linux-${ARCH}.zip
    unzip -q android-ndk-r12b-linux-${ARCH}.zip
    popd
fi

echo y | sdkmanager "platforms;android-${ANDROID_COMPILE_SDK}" >/dev/null
echo y | sdkmanager "platform-tools" >/dev/null
echo y | sdkmanager "build-tools;${ANDROID_BUILD_TOOLS}" >/dev/null
cp local.properties.github local.properties
git submodule update --init
# backup
mkdir -p ./backup/armeabi-v7a ./backup/x86
cp ./src/main/libs/armeabi-v7a/libgojni.so ./backup/armeabi-v7a; cp ./src/main/libs/x86/libgojni.so ./backup/x86
sbt native-build
cp ./backup/armeabi-v7a/libgojni.so ./src/main/libs/armeabi-v7a; cp ./backup/x86/libgojni.so ./src/main/libs/x86
sbt android:package-release
