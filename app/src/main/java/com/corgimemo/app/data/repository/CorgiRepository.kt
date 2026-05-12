package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.CorgiDao
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorgiRepository @Inject constructor(
    private val corgiDao: CorgiDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun insertCorgi(corgi: CorgiData) = withContext(ioDispatcher) {
        corgiDao.insert(corgi)
    }

    suspend fun updateCorgi(corgi: CorgiData) = withContext(ioDispatcher) {
        corgiDao.update(corgi)
    }

    suspend fun getCorgiData(): CorgiData? = withContext(ioDispatcher) {
        corgiDao.getCorgiData()
    }

    fun getCorgiDataFlow(): Flow<CorgiData?> = corgiDao.getCorgiDataFlow()

    suspend fun addExperience(amount: Int) = withContext(ioDispatcher) {
        corgiDao.addExperience(amount)
    }

    suspend fun updateLevel(level: Int) = withContext(ioDispatcher) {
        corgiDao.updateLevel(level)
    }

    suspend fun updateMood(mood: Int) = withContext(ioDispatcher) {
        corgiDao.updateMood(mood)
    }

    suspend fun updateOutfit(outfit: String?) = withContext(ioDispatcher) {
        corgiDao.updateOutfit(outfit)
    }

    suspend fun updateUnlockedOutfits(outfits: String) = withContext(ioDispatcher) {
        corgiDao.updateUnlockedOutfits(outfits)
    }

    suspend fun updateLastActiveDate(date: String) = withContext(ioDispatcher) {
        corgiDao.updateLastActiveDate(date)
    }

    suspend fun incrementTotalCompleted() = withContext(ioDispatcher) {
        corgiDao.incrementTotalCompleted()
    }

    suspend fun incrementConsecutiveDays() = withContext(ioDispatcher) {
        corgiDao.incrementConsecutiveDays()
    }

    suspend fun resetConsecutiveDays() = withContext(ioDispatcher) {
        corgiDao.resetConsecutiveDays()
    }
}
