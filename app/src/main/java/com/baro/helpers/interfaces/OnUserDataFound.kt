package com.baro.helpers.interfaces

import com.baro.helpers.AsyncHelpers

interface OnUserDataFound {
    fun onDataReturned(userData: AsyncHelpers.LoadUserData.LoadUserDataResponse?)
}