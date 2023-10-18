#!/bin/bash

echo 'Building UI'
cd app
echo 'Cleaning Builds'
rm -rf app/build
rm -rf app/kernel-linux
rm -rf app/stage/build
npm install && npm run build:mobile
cd ..

echo 'Building Kernel'

cd kernel
gomobile bind --tags fts5 -ldflags '-s -w' -v -o android/app/libs/kernel.aar -target='android/arm64' -androidapi 24 ./mobile/
cd ..

echo 'Building Resource'
cd app
zip -r android/app/src/main/assets/app.zip appearance/ guide/ stage/ changelogs/
