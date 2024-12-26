#!/bin/bash

# Update the package list
apt-get update

# Install pkg-config
apt-get install -y pkg-config

# Find the path to pkg-config
PKG_CONFIG_PATH=$(which pkg-config)

# Export the PKG_CONFIG_EXECUTABLE environment variable
export PKG_CONFIG_EXECUTABLE=$PKG_CONFIG_PATH

# Print the pkg-config path
echo "PKG_CONFIG_EXECUTABLE is set to $PKG_CONFIG_EXECUTABLE"