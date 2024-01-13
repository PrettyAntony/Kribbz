@SuppressLint("CheckResult")
class InspectionDetailsRepository : BaseRepository() {

    val mAddOrEditLeadResponseLiveData: MutableLiveData<WebServiceResponse.AddOrEditLeadResponse> =
        MutableLiveData()
    val mArrivalStatusChangeResponseLiveData: MutableLiveData<WebServiceResponse.ChangeInspectorArrivalStatusResponse> =
        MutableLiveData()
    private var mCompositeDisposable = CompositeDisposable()
    val mDynamicFormData: MutableLiveData<ArrayList<TabAndValues>> = MutableLiveData()
    val mMediaDetailsLiveData: MutableLiveData<List<FileDetails>?> = MutableLiveData()
    val mPropertyDetailsResponseLiveData: MutableLiveData<WebServiceResponse.PropertyDetailsResponse> =
        MutableLiveData()
    val mRemoveMediaResponseLiveData: MutableLiveData<WebServiceResponse.RemoveMediaResponse> =
        MutableLiveData()
    val mSaveMediaResponseLiveData: MutableLiveData<WebServiceResponse.SaveMediaResponse> =
        MutableLiveData()
    val mTaskErrorCode: MutableLiveData<ErrorCodeManager> = MutableLiveData()

    fun addDynamicFormValuesToDb(result: List<DfKeyAnswer>) {

        val taskData = DfCreateTaskData().initAnswerMember(result)
        val disposable =
            RxTask().call<DynamicFormInsertTask, DBTaskResultBase>(taskData).doFinally {
            }.subscribe {
                if (it.isSuccess) {
                }
            }
        mCompositeDisposable.add(disposable)
    }

    fun addDynamicFormValuesToDb(leadId: Int, jsonObject: JSONObject) {

        val disposable =
            RxTask().call<DynamicFormInsertTask, DBTaskResultBase>(leadId, jsonObject).doFinally {
            }.subscribe {
                if (it.isSuccess) {
                }
            }
        mCompositeDisposable.add(disposable)
    }

    fun callAddOrEditLeadWebservice(request: WebServiceRequest.AddOrEditLeadRequest) {
        val disposable = WebServiceManager.service
            .callAddOrEditLeadWebService(AppState.user.sessionToken, request)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                if (result.code == WebConstants.Status.CODE_SUCCESS.status) {
                    updateLeadIdToDB(
                        request.idLeadScheduleUnique.toInt(),
                        result.response.offer_uid.toInt()
                    )
                    mAddOrEditLeadResponseLiveData.value = result
                } else {

                    val errorCodeManager = ErrorCodeManager()
                    errorCodeManager.errorCode = result.code
                    errorCodeManager.message = result.message
                    errorCodeManager.taskId = WebConstants.WebServiceTaskId.ADD_OR_EDIT_LEAD.taskId
                    mTaskErrorCode.value = errorCodeManager
                }
            }, { error ->
                val errorCodeManager = ErrorCodeManager()
                if (error is SocketTimeoutException) {
                    errorCodeManager.errorCode =
                        WebConstants.Status.CODE_UNABLE_TO_CONNECT_TO_SERVER.status
                } else
                    errorCodeManager.errorCode = WebConstants.Status.CODE_UNEXPECTED_ERROR.status
                errorCodeManager.taskId = WebConstants.WebServiceTaskId.ADD_OR_EDIT_LEAD.taskId
                mTaskErrorCode.value = errorCodeManager
            })
        mCompositeDisposable.add(disposable)
    }

    fun callChangeInspectorArrivalStatusService(request: WebServiceRequest.ChangInspectorArrivalStatus) {
        val disposable = WebServiceManager.service
            .callChangeInspectorArrivalStatusService(AppState.user.sessionToken, request)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                if (result.code == WebConstants.Status.CODE_SUCCESS.status) {

                    mArrivalStatusChangeResponseLiveData.value = result
                } else {

                    val errorCodeManager = ErrorCodeManager()
                    errorCodeManager.errorCode = result.code
                    errorCodeManager.message = result.message
                    errorCodeManager.taskId =
                        WebConstants.WebServiceTaskId.CHANGE_INSPECTOR_ARRIVAL_STATUS.taskId
                    mTaskErrorCode.value = errorCodeManager
                }
            }, { error ->
                val errorCodeManager = ErrorCodeManager()
                if (error is SocketTimeoutException) {
                    errorCodeManager.errorCode =
                        WebConstants.Status.CODE_UNABLE_TO_CONNECT_TO_SERVER.status
                } else
                    errorCodeManager.errorCode = WebConstants.Status.CODE_UNEXPECTED_ERROR.status
                errorCodeManager.taskId =
                    WebConstants.WebServiceTaskId.CHANGE_INSPECTOR_ARRIVAL_STATUS.taskId
                mTaskErrorCode.value = errorCodeManager
            })
        mCompositeDisposable.add(disposable)
    }

    fun callPropertyDetail(leadId: Int) {
        val disposable = WebServiceManager.service
            .callPropertyDetailWebService(AppState.user.sessionToken, leadId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                if (result.code == WebConstants.Status.CODE_SUCCESS.status) {
                    mPropertyDetailsResponseLiveData.value = result
                } else {
                    val errorCodeManager = ErrorCodeManager()
                    errorCodeManager.errorCode = result.code
                    errorCodeManager.message = result.message
                    errorCodeManager.taskId = WebConstants.WebServiceTaskId.PROPERTY_DETAIL.taskId
                    mTaskErrorCode.value = errorCodeManager
                }
            }, { error ->
                val errorCodeManager = ErrorCodeManager()
                if (error is SocketTimeoutException) {
                    errorCodeManager.errorCode =
                        WebConstants.Status.CODE_UNABLE_TO_CONNECT_TO_SERVER.status
                } else
                    errorCodeManager.errorCode = WebConstants.Status.CODE_UNEXPECTED_ERROR.status
                errorCodeManager.taskId = WebConstants.WebServiceTaskId.PROPERTY_DETAIL.taskId
                mTaskErrorCode.value = errorCodeManager
            })
        mCompositeDisposable.add(disposable)
    }

    fun callOwnerListingDetail(leadId: Int) {
        val disposable = WebServiceManager.service
            .callOwnerListingDetail(AppState.user.sessionToken, leadId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                if (result.code == WebConstants.Status.CODE_SUCCESS.status) {
                    mPropertyDetailsResponseLiveData.value = result
                } else {
                    mTaskErrorCode.value = getError(
                        result.code, result.message,
                        WebConstants.WebServiceTaskId.OWNER_LEAD_DETAIL.taskId
                    )
                }
            }, { error ->
                mTaskErrorCode.value =
                    getError(error, WebConstants.WebServiceTaskId.OWNER_LEAD_DETAIL.taskId)
            })
        mCompositeDisposable.add(disposable)
    }

    fun callRemoveMediaWebService(request: WebServiceRequest.RemoveMediaRequest) {
        val disposable = WebServiceManager.service
            .callRemoveMediaWebService(AppState.user.sessionToken, request)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                if (result.code == WebConstants.Status.CODE_SUCCESS.status) {

                    mRemoveMediaResponseLiveData.value = result
                } else {

                    val errorCodeManager = ErrorCodeManager()
                    errorCodeManager.errorCode = result.code
                    errorCodeManager.message = result.message
                    errorCodeManager.taskId = WebConstants.WebServiceTaskId.REMOVE_MEDIA.taskId
                    mTaskErrorCode.value = errorCodeManager
                }
            }, { error ->
                val errorCodeManager = ErrorCodeManager()
                if (error is SocketTimeoutException) {
                    errorCodeManager.errorCode =
                        WebConstants.Status.CODE_UNABLE_TO_CONNECT_TO_SERVER.status
                } else
                    errorCodeManager.errorCode = WebConstants.Status.CODE_UNEXPECTED_ERROR.status
                errorCodeManager.taskId = WebConstants.WebServiceTaskId.REMOVE_MEDIA.taskId
                mTaskErrorCode.value = errorCodeManager
            })
        mCompositeDisposable.add(disposable)
    }

    fun callSaveMediaWebService(request: WebServiceRequest.SaveMediaRequest) {
        val disposable = WebServiceManager.service
            .callSaveMediaWebService(AppState.user.sessionToken, request)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                if (result.code == WebConstants.Status.CODE_SUCCESS.status) {

                    mSaveMediaResponseLiveData.value = result
                } else {

                    val errorCodeManager = ErrorCodeManager()
                    errorCodeManager.errorCode = result.code
                    errorCodeManager.message = result.message
                    errorCodeManager.taskId = WebConstants.WebServiceTaskId.SAVE_MEDIA.taskId
                    mTaskErrorCode.value = errorCodeManager
                }
            }, { error ->
                val errorCodeManager = ErrorCodeManager()
                if (error is SocketTimeoutException) {
                    errorCodeManager.errorCode =
                        WebConstants.Status.CODE_UNABLE_TO_CONNECT_TO_SERVER.status
                } else
                    errorCodeManager.errorCode = WebConstants.Status.CODE_UNEXPECTED_ERROR.status
                errorCodeManager.taskId = WebConstants.WebServiceTaskId.SAVE_MEDIA.taskId
                mTaskErrorCode.value = errorCodeManager
            })
        mCompositeDisposable.add(disposable)
    }

    fun deleteMediaFromDb(fileDetails: FileDetails) {

        val deleteTaskData = MediaTaskDatas.DeleteTaskData(fileDetails)
        val disposable =
            RxTask().call<MediaDbDeleteTask, DBTaskResultBase>(deleteTaskData).doFinally {
            }.subscribe {
                if (it.isSuccess) {
                }
            }
        mCompositeDisposable.add(disposable)
    }

    fun getFormQuestionnaireFromDb(leadId: Int) {

        val disposable =
            RxTask().call<DynamicFormReadTask, ReadFormTabTaskResult>(
                DfSelectedValuesTaskData(
                    leadId
                )
            ).doFinally {
            }.subscribe {
                if (it.isSuccess) {
                    mDynamicFormData.value = it.formList
                }
            }
        mCompositeDisposable.add(disposable)
    }

    fun getMediaItemsFromDb(propertyId: Int) {

        val taskData = MediaTaskDatas.PropertyReadTaskData(propertyId)
        val disposable =
            RxTask().call<MediaDbReadTask, MediaDetailsDbReadTaskResult>(taskData).doFinally {
            }.subscribe {
                if (it.isSuccess) {
                    mMediaDetailsLiveData.value = it.fileDetailsList
                }
            }
        mCompositeDisposable.add(disposable)
    }

    fun onCleared() {
        mCompositeDisposable.clear()
    }

    fun saveImageToDb(
        leadId: Int,
        mediaModel: BaseConstants.MediaModels,
        imageList: List<ImageGallery>
    ) {

        for (i: Int in 0 until imageList.size) {

            val image = imageList[i]
            val filename = URLUtil.guessFileName(image.mediaOrigininal, null, null)

            val media = KribbzMedia(image.idPhoto, i, leadId, mediaModel.value, false, filename)
            val disposable = RxTask().call<MediaDbInsertTask, DBTaskResultBase>(media).doFinally {
            }.subscribe {
                if (it.isSuccess) {
                }
            }
            mCompositeDisposable.add(disposable)
        }

    }

    fun saveMediaToDb(order: Int, saveMediaData: SaveMediaData, fileDetails: FileDetails) {

        val media = KribbzMedia(
            mediaId = saveMediaData.mediaId,
            sequence = order,
            linkId = fileDetails.propertyId,
            linkType = BaseConstants.MediaModels.PROPERTY.value,
            isVideo = fileDetails.isVideo,
            fileName = fileDetails.fileName
        )

        val disposable = RxTask().call<MediaDbInsertTask, DBTaskResultBase>(media).doFinally {
        }.subscribe {
            if (it.isSuccess) {
            }
        }
        mCompositeDisposable.add(disposable)
    }

    fun saveVideoToDb(
        leadId: Int,
        mediaModel: BaseConstants.MediaModels,
        imageList: List<VideoGallery>
    ) {

        for (i: Int in 0 until imageList.size) {

            val video = imageList[i]
            val media = KribbzMedia(
                sequence = i,
                linkId = leadId,
                linkType = mediaModel.value,
                isVideo = true,
                fileName = video.video_link
            )
            val disposable = RxTask().call<MediaDbInsertTask, DBTaskResultBase>(media).doFinally {
            }.subscribe {
                if (it.isSuccess) {
                }
            }
            mCompositeDisposable.add(disposable)
        }
    }

    fun updateLeadIdToDB(id: Int, status: Int) {

        val taskData = InspectionVisitUpdateLeadIdTaskData(id, status)
        val disposable =
            RxTask().call<InspectionVisitDBUpdateLeadIdTask, DBTaskResultBase>(taskData).doFinally {
            }.subscribe {
                if (it.isSuccess) {
                    // updated successfully
                }
            }
        mCompositeDisposable.add(disposable)
    }

    fun uploadMedia(fileDetails: FileDetails) {
        MediaManager.uploadMedia(fileDetails)
    }

    fun uploadMediaList(list: List<FileDetails>) {
        MediaManager.uploadMediaList(list)
    }
}