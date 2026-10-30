cmake_minimum_required(VERSION 3.22.1)
project(espmath LANGUAGES CXX)
add_library(espmath SHARED esp.cpp)
target_compile_features(espmath PRIVATE cxx_std_17)
