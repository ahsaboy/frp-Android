package io.github.acedroidx.frp

import android.content.Context
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class FrpConfig(
    val type: FrpType,
    val fileName: String,
) : Parcelable {
    override fun toString(): String {
        return "[$type]$fileName"
    }

    fun getDir(context: Context): File {
        return this.type.getDir(context)
    }

    fun getFile(context: Context): File {
        return File(this.getDir(context), this.fileName)
    }

    fun getLogDir(context: Context): File {
        return File(context.filesDir, "logs/${this.type.typeName}")
    }

    fun getLogFile(context: Context): File {
        val logFileName = this.fileName.replace(".toml", ".log")
        return File(this.getLogDir(context), logFileName)
    }
}
