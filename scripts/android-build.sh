#!/bin/bash


echo 'Cleaning Builds'
rm -rf app/build
rm -rf app/kernel-linux
rm -rf app/stage/build
rm -rf android/app/libs
rm -rf android/app/src/main/assets
echo 'Building UI'
cd app
npm install && npm run build:mobile
cd ..

echo 'Building Kernel'

mkdir -p android/app/libs
mkdir -p android/app/src/main/assets

cd kernel
gomobile bind --tags fts5 -ldflags '-s -w' -v -o ../android/app/libs/kernel.aar -target='android/arm64' -androidapi 24 ./mobile/
cd ..
rm android/app/libs/kernel-sources.jar
echo 'Building Resource'
cd app
zip -r ../android/app/src/main/assets/app.zip appearance/ guide/ stage/ changelogs/
