package app.traveldiary.data.diary

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlacePhotosTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var directory: File
    private lateinit var photos: PlacePhotos

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        directory = folder.newFolder("photos")
        photos = PlacePhotos(directory, context.contentResolver)
    }

    @Test
    fun bigPhotosAreScaledDown() = runBlocking {
        val photo = stored(photos.save(folder.jpeg(3000, 1500)))
        assertEquals(PlacePhotos.MAX_SIZE, photo.width)
        assertEquals(1024, photo.height)
    }

    @Test
    fun smallPhotosKeepTheirSize() = runBlocking {
        val photo = stored(photos.save(folder.jpeg(640, 480)))
        assertEquals(640, photo.width)
        assertEquals(480, photo.height)
    }

    @Test
    fun photosAreTurnedUpright() = runBlocking {
        // Saved sideways: a quarter turn clockwise brings its left (red) half to the top.
        val photo = stored(photos.save(folder.jpeg(300, 200, ExifInterface.ORIENTATION_ROTATE_90)))
        assertEquals(200, photo.width)
        assertEquals(300, photo.height)
        assertTrue(photo.getPixel(100, 50).isRed())
        assertTrue(photo.getPixel(100, 250).isBlue())
    }

    @Test
    fun otherFilesAreRejected() {
        val text = folder.newFile().apply { writeText("not a photo") }
        assertThrows(IOException::class.java) { runBlocking { photos.save(Uri.fromFile(text)) } }
        assertTrue(directory.list().isNullOrEmpty())
    }

    @Test
    fun deletesPhotos() = runBlocking {
        val first = photos.save(folder.jpeg(64, 48))
        val second = photos.save(folder.jpeg(64, 48))

        photos.delete(first)
        assertEquals(listOf(second), directory.list()!!.toList())

        photos.deleteAll()
        assertTrue(directory.list()!!.isEmpty())
    }

    @Test
    fun decodesAtLeastTheStoredSize() {
        assertEquals(1, PlacePhotos.sampleSize(3000))
        assertEquals(2, PlacePhotos.sampleSize(4096))
        assertEquals(2, PlacePhotos.sampleSize(8191))
        assertEquals(4, PlacePhotos.sampleSize(8192))
    }

    private fun stored(name: String): Bitmap = BitmapFactory.decodeFile(photos.file(name).path)

    private fun Int.isRed() = Color.red(this) > 200 && Color.blue(this) < 60

    private fun Int.isBlue() = Color.blue(this) > 200 && Color.red(this) < 60
}
