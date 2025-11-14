cmake_minimum_required(VERSION 3.10.2)

project("edgeviewer")

# --- Set OpenCV Directory ---
# ${CMAKE_SOURCE_DIR} is: app/src/main/cpp
# ../../.. takes us to the project root (EdgeViewer)
# Then we go into opencv/sdk/native/jni
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/../../../../opencv/sdk/native/jni)


# --- Find OpenCV ---
find_package(OpenCV REQUIRED)

# --- Build Native Library ---
add_library(
        edgeviewer
        SHARED
        native-lib.cpp
)

# --- Android Logs ---
find_library(
        log-lib
        log
)

# --- Include OpenCV Headers ---
target_include_directories(edgeviewer PRIVATE ${OpenCV_INCLUDE_DIRS})

# --- Link OpenCV + log ---
target_link_libraries(
        edgeviewer
        ${OpenCV_LIBS}
        ${log-lib}
)
