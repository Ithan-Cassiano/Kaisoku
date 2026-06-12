package com.kosen.reader.core.model.parcelable

import android.os.Parcel
import kotlinx.parcelize.Parceler
import com.kosen.reader.core.model.MangaSource
import com.kosen.reader.parsers.model.MangaSource

class MangaSourceParceler : Parceler<MangaSource> {

	override fun create(parcel: Parcel): MangaSource = MangaSource(parcel.readString())

	override fun MangaSource.write(parcel: Parcel, flags: Int) {
		parcel.writeString(name)
	}
}
