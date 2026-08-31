cmake_minimum_required(VERSION 3.22.1)
project("xrc_guard")

add_library(xrc_guard SHARED src/main/cpp/guard.c)

find_library(log-lib log)

target_link_libraries(xrc_guard ${log-lib})
