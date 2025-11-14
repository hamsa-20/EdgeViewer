cmake_minimum_required(VERSION 3.10.2)

project("edgeviewer")

# --- Set OpenCV Directory ---
# Try different possible paths for OpenCV
set(OPENCV_PATHS
        ${CMAKE_SOURCE_DIR}/../../../opencv/sdk/native/jni
        ${CMAKE_SOURCE_DIR}/../../../../opencv/sdk/native/jni
        ${CMAKE_SOURCE_DIR}/../../../../../opencv/sdk/native/jni
        ${CMAKE_SOURCE_DIR}/../../../../../../opencv/sdk/native/jni
)

# Find OpenCV directory
foreach(OPENCV_PATH ${OPENCV_PATHS})
    if(EXISTS ${OPENCV_PATH})
        set(OpenCV_DIR ${OPENCV_PATH})
        message(STATUS "Found OpenCV at: ${OpenCV_DIR}")
        break()
    endif()
endforeach()

if(NOT OpenCV_DIR)
    message(FATAL_ERROR "OpenCV not found. Please check the OpenCV Android SDK path.")
endif()

# --- Find OpenCV ---
find_package(OpenCV REQUIRED)

if(OpenCV_FOUND)
    message(STATUS "OpenCV found: ${OpenCV_VERSION}")
    message(STATUS "OpenCV libraries: ${OpenCV_LIBS}")
    message(STATUS "OpenCV includes: ${OpenCV_INCLUDE_DIRS}")
else()
    message(FATAL_ERROR "OpenCV package not found")
endif()

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