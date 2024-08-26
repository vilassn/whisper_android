## Define your project and its version
#cmake_minimum_required(VERSION 3.0)
#project(TFLiteEngine VERSION 1.0)
#
## Specify where to find the header files for TF Lite C++
#set(INCLUDE_DIRS
#        ${CMAKE_CURRENT_LIST_DIR}/tf-lite-api/tensorflow_src
#        ${CMAKE_CURRENT_LIST_DIR}/tf-lite-api/include/flatbuffers/include
#        )
#
## Check if the build is Android
#if (ANDROID)
#    add_library(audioEngine SHARED TFLiteEngine.cpp TFLiteEngineJNI.cpp)
#    target_include_directories(audioEngine PRIVATE ${INCLUDE_DIRS})
#
#    # Add 'tflite' library (imported)
#    add_library(tflite SHARED IMPORTED)
#    set_target_properties(tflite PROPERTIES IMPORTED_LOCATION
#            ${CMAKE_CURRENT_LIST_DIR}/tf-lite-api/generated-libs/${ANDROID_ABI}/libtensorflowlite.so)
#
#    # Add Qualcomm libraries
#    if(${ANDROID_ABI} STREQUAL "arm64-v8a")
#        # Add Qualcomm ARM64 libraries
#        add_library(qualcomm_arm64 SHARED IMPORTED)
#        set_target_properties(qualcomm_arm64 PROPERTIES IMPORTED_LOCATION
#                ${CMAKE_CURRENT_LIST_DIR}/jniLibs/arm64-v8a/libqualcomm_arm64.so)
#
#        target_link_libraries(audioEngine tflite qualcomm_arm64)
#    endif()
#
#    if(${ANDROID_ABI} STREQUAL "hexagon-v75")
#        # Add Qualcomm Hexagon libraries
#        add_library(qualcomm_hexagon SHARED IMPORTED)
#        set_target_properties(qualcomm_hexagon PROPERTIES IMPORTED_LOCATION
#                ${CMAKE_CURRENT_LIST_DIR}/jniLibs/hexagon-v75/libqualcomm_hexagon.so)
#
#        target_link_libraries(audioEngine tflite qualcomm_hexagon)
#    endif()
#
#endif()
#
## Check if the build is not Android
#if (NOT ANDROID)
#    add_library(audioEngine SHARED TFLiteEngine.cpp)
#    target_include_directories(audioEngine PRIVATE ${INCLUDE_DIRS})
#
#    # Add 'tflite' library (imported)
#    add_library(tflite SHARED IMPORTED)
#    set_target_properties(tflite PROPERTIES IMPORTED_LOCATION
#            ${CMAKE_CURRENT_LIST_DIR}/tf-lite-api/generated-libs/x86_64/libtensorflowlite.so)
#
#    # Create an executable target that links with 'audioEngine' and 'tflite' libraries
#    add_executable(my_audio_app main.cpp)
#    target_link_libraries(my_audio_app PRIVATE audioEngine tflite)
#endif ()
#
#
#
#
#
#
