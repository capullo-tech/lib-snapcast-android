package tech.capullo.lib_snapcast_android

class NativeLib {

    /**
     * A native method that is implemented by the 'lib_snapcast_android' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'lib_snapcast_android' library on application startup.
        init {
            System.loadLibrary("lib_snapcast_android")
        }
    }
}