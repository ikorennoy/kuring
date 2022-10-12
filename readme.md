# KUring

[![Build](https://github.com/ikorennoy/jasyncfio/actions/workflows/build.yml/badge.svg)](https://github.com/ikorennoy/jasyncfio/actions/workflows/build.yml)

KUring provides an asynchronous file I/O API based on the Linux io_uring interface.

## KUring Features

* Fully asynchronous io_uring based file I/O API
* API comes in two kinds: Buffered and Direct I/O
* API for linear access to file (depends on your file system)
* Using a wide range of io_uring features such as polling, registered buffers/files

## Examples
```kotlin
val executor = EventExecutor.initDefault()
val file = AsyncFile.open(Paths.get("path/to/file"), eventExecutor)
val buffer = ByteBuffer.allocateDirect(1024)
val readBytes = file.read(buffer)
```
