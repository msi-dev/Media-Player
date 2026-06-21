cmake_minimum_required(VERSION 3.22.1)

project("media-core")

add_library(
    media-core
    SHARED
    src/main/cpp/media-core.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    media-core
    ${log-lib}
)
