# Define your project and its version
cmake_minimum_required(VERSION 3.0)
project(TFLiteEngine VERSION 1.0)

# Specify where to find the header files for TF Lite C++
set(INCLUDE_DIRS
        ${CMAKE_CURRENT_LIST_DIR}/tf-lite-api/tensorflow_src
        ${CMAKE_CURRENT_LIST_DIR}/tf-lite-api/include/flatbuffers/include
        )

# # Check if the build is Android
if (ANDROID)
    add_library(audioEngine SHARED TFLiteEngine.cpp TFLiteEngineJNI.cpp)
    target_include_directories(audioEngine PRIVATE ${INCLUDE_DIRS})

    # Add 'tflite' library (imported)
    add_library(tflite SHARED IMPORTED)
    set_target_properties(tflite PROPERTIES IMPORTED_LOCATION
            ${CMAKE_CURRENT_LIST_DIR}/tf-lite-api/generated-libs/${ANDROID_ABI}/libtensorflowlite.so)

    target_link_libraries(audioEngine tflite)
endif ()

# Check if the build is not Android
if (NOT ANDROID)
    add_library(audioEngine SHARED TFLiteEngine.cpp)
    target_include_directories(audioEngine PRIVATE ${INCLUDE_DIRS})

    # Add 'tflite' library (imported)
    add_library(tflite SHARED IMPORTED)
    set_target_properties(tflite PROPERTIES IMPORTED_LOCATION
            ${CMAKE_CURRENT_LIST_DIR}/tf-lite-api/generated-libs/x86_64/libtensorflowlite.so)

    # Create an executable target that links with 'audioEngine' and 'tflite' libraries
    add_executable(my_audio_app main.cpp)
    target_link_libraries(my_audio_app PRIVATE audioEngine tflite)
endif ()






