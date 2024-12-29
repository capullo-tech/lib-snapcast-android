#!/bin/bash

# Set the version to be installed
PKG_CONFIG_VERSION=0.29.2

# Download and extract pkg-config source code
wget https://pkg-config.freedesktop.org/releases/pkg-config-$PKG_CONFIG_VERSION.tar.gz
tar -xzf pkg-config-$PKG_CONFIG_VERSION.tar.gz
cd pkg-config-$PKG_CONFIG_VERSION

# Configure, compile, and install pkg-config to a custom directory
./configure --prefix=$HOME/pkg-config --with-internal-glib
make
make install

# Set the PKG_CONFIG_EXECUTABLE environment variable
#export PKG_CONFIG_EXECUTABLE=$HOME/pkg-config/bin/pkg-config
#
## Print the pkg-config path
#echo "PKG_CONFIG_EXECUTABLE is set to $PKG_CONFIG_EXECUTABLE"
#
## Path to snapcast's CMakeLists.txt file
#CMAKE_FILE="$HOME/build/lib-snapcast-android/src/main/cpp/snapcast/CMakeLists.txt"
#
## Line to add to the CMake file
#LINE_TO_ADD="set(PKG_CONFIG_EXECUTABLE \"$PKG_CONFIG_EXECUTABLE\")"
#
## Add the line to the end of the CMake file if it doesn't already exist
## grep -qxF "$LINE_TO_ADD" $CMAKE_FILE || echo "$LINE_TO_ADD" >> $CMAKE_FILE
## sed -i "7a $LINE_TO_ADD" $CMAKE_FILE
#
#
## Print the contents of the CMakeLists.txt file for verification
#echo "Contents of $CMAKE_FILE:"
#cat $CMAKE_FILE
#
#echo "Added line to CMake file: $LINE_TO_ADD"
#