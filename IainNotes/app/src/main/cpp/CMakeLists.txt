cmake_minimum_required(VERSION 3.22.1)
project(xyncrypto C)

set(SODIUM_DIR ${CMAKE_SOURCE_DIR}/libsodium/${ANDROID_ABI})

add_library(sodium STATIC IMPORTED)
set_target_properties(sodium PROPERTIES
        IMPORTED_LOCATION ${SODIUM_DIR}/lib/libsodium.a)

add_library(xyncrypto SHARED xyncrypto.c)
target_include_directories(xyncrypto PRIVATE ${SODIUM_DIR}/include)
target_link_libraries(xyncrypto sodium)