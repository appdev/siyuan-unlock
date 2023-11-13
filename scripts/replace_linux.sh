#!/bin/bash

# 仅限于在安装了思源官方版本的情况下，使用此脚本替换官方版本
# 这个脚本仅在 Arch Linux 上测试过
rm -rf /usr/lib/siyuan-note/*
rm  /usr/bin/siyuan-note
cp -r app/build/linux-unpacked/* /usr/lib/siyuan-note/
cp -r scripts/siyuan-note /usr/bin/siyuan-note
