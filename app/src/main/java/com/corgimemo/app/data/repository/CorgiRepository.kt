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
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
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

    /**
     * 更新已解锁成就列表
     *
     * @param achievements 成就 ID JSON 字符串
     */
    suspend fun updateUnlockedAchievements(achievements: String) = withContext(ioDispatcher) {
        corgiDao.updateUnlockedAchievements(achievements)
    }

    /**
     * 更新历史最长连续天数
     *
     * @param days 天数
     */
    suspend fun updateMaxConsecutiveDays(days: Int) = withContext(ioDispatcher) {
        corgiDao.updateMaxConsecutiveDays(days)
    }

    /**
     * 更新柯基名字
     *
     * @param name 新的柯基名字
     */
    suspend fun updateCorgiName(name: String) = withContext(ioDispatcher) {
        corgiDao.updateName(name)
    }

    /**
     * 更新用户头像路径
     *
     * 字段语义：详见 CorgiData.avatarPath 注释。
     * - path 非空：UI 用 Coil AsyncImage 加载（本期上传未接，调用方不传非空）
     * - path 为 null：UI 回退到首字母占位徽章
     *
     * @param path 头像文件绝对路径 / content URI；传 null 表示清除
     */
    suspend fun updateAvatarPath(path: String?) = withContext(ioDispatcher) {
        corgiDao.updateAvatarPath(path)
    }

    /**
     * 更新性别字段
     *
     * 字段语义：详见 CorgiData.gender 注释。
     * - gender = "MALE" / "FEMALE" / "OTHER" → UI 展示对应性别徽章
     * - gender = null                          → UI 用中性占位
     *
     * @param gender 性别值；传 null 表示清除（恢复未设置）
     */
    suspend fun updateGender(gender: String?) = withContext(ioDispatcher) {
        corgiDao.updateGender(gender)
    }

    /**
     * 更新用户签名
     *
     * @param signature 签名文本
     */
    suspend fun updateSignature(signature: String) = withContext(ioDispatcher) {
        corgiDao.updateSignature(signature)
    }
}
