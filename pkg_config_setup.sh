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
