/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.roomdirectory.createroom

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.activityViewModel
import com.airbnb.mvrx.withState
import com.yalantis.ucrop.UCrop
import im.vector.app.R
import im.vector.app.core.dialogs.GalleryOrCameraDialogHelper
import im.vector.app.core.extensions.cleanup
import im.vector.app.core.extensions.configureWith
import im.vector.app.core.platform.OnBackPressed
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.features.media.createUCropWithDefaultSettings
import im.vector.app.features.roomdirectory.RoomDirectorySharedAction
import im.vector.app.features.roomdirectory.RoomDirectorySharedActionViewModel
import im.vector.lib.multipicker.entity.MultiPickerImageType
import kotlinx.android.synthetic.main.fragment_create_room.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class CreateRoomFragment @Inject constructor(
        private val createRoomController: CreateRoomController
) : VectorBaseFragment(),
        CreateRoomController.Listener,
        GalleryOrCameraDialogHelper.Listener,
        OnBackPressed {

    private lateinit var sharedActionViewModel: RoomDirectorySharedActionViewModel
    private val viewModel: CreateRoomViewModel by activityViewModel()

    private val galleryOrCameraDialogHelper = GalleryOrCameraDialogHelper(this)

    override fun getLayoutResId() = R.layout.fragment_create_room

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vectorBaseActivity.setSupportActionBar(createRoomToolbar)
        sharedActionViewModel = activityViewModelProvider.get(RoomDirectorySharedActionViewModel::class.java)
        setupRecyclerView()
        createRoomClose.debouncedClicks {
            sharedActionViewModel.post(RoomDirectorySharedAction.Back)
        }
    }

    override fun onDestroyView() {
        createRoomForm.cleanup()
        createRoomController.listener = null
        super.onDestroyView()
    }

    private fun setupRecyclerView() {
        createRoomForm.configureWith(createRoomController)
        createRoomController.listener = this
    }

    override fun onAvatarDelete() {
        viewModel.handle(CreateRoomAction.SetAvatar(null))
    }

    override fun onAvatarChange() {
        galleryOrCameraDialogHelper.show()
    }

    override fun onImageReady(image: MultiPickerImageType) {
        val destinationFile = File(requireContext().cacheDir, "${image.displayName}_edited_image_${System.currentTimeMillis()}")
        val uri = image.contentUri
        createUCropWithDefaultSettings(requireContext(), uri, destinationFile.toUri(), image.displayName)
                .withAspectRatio(1f, 1f)
                .start(requireContext(), this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // TODO handle this one (Ucrop lib)
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                UCrop.REQUEST_CROP ->
                    viewModel.handle(CreateRoomAction.SetAvatar(data?.let { UCrop.getOutput(it) }))
            }
        }
    }

    override fun onNameChange(newName: String) {
        viewModel.handle(CreateRoomAction.SetName(newName))
    }

    override fun onTopicChange(newTopic: String) {
        viewModel.handle(CreateRoomAction.SetTopic(newTopic))
    }

    override fun setIsPublic(isPublic: Boolean) {
        viewModel.handle(CreateRoomAction.SetIsPublic(isPublic))
    }

    override fun setIsInRoomDirectory(isInRoomDirectory: Boolean) {
        viewModel.handle(CreateRoomAction.SetIsInRoomDirectory(isInRoomDirectory))
    }

    override fun setIsEncrypted(isEncrypted: Boolean) {
        viewModel.handle(CreateRoomAction.SetIsEncrypted(isEncrypted))
    }

    override fun submit() {
        viewModel.handle(CreateRoomAction.Create)
    }

    override fun retry() {
        Timber.v("Retry")
        viewModel.handle(CreateRoomAction.Create)
    }

    override fun onBackPressed(toolbarButton: Boolean): Boolean {
        viewModel.handle(CreateRoomAction.Reset)
        return false
    }

    override fun invalidate() = withState(viewModel) { state ->
        val async = state.asyncCreateRoomRequest
        if (async is Success) {
            // Navigate to freshly created room
            navigator.openRoom(requireActivity(), async())

            sharedActionViewModel.post(RoomDirectorySharedAction.Close)
        } else {
            // Populate list with Epoxy
            createRoomController.setData(state)
        }
    }
}
