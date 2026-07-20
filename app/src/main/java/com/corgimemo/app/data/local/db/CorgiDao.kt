package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.CorgiData
import kotlinx.coroutines.flow.Flow

@Dao
interface CorgiDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(corgi: CorgiData)

    @Update
    suspend fun update(corgi: CorgiData)

    @Query("SELECT * FROM corgi_data LIMIT 1")
    suspend fun getCorgiData(): CorgiData?

    @Query("SELECT * FROM corgi_data LIMIT 1")
    fun getCorgiDataFlow(): Flow<CorgiData?>

    @Query("UPDATE corgi_data SET experience = experience + :amount")
    suspend fun addExperience(amount: Int)

    @Query("UPDATE corgi_data SET level = :level")
    suspend fun updateLevel(level: Int)

    @Query("UPDATE corgi_data SET moodValue = :mood")
    suspend fun updateMood(mood: Int)

    @Query("UPDATE corgi_data SET currentOutfit = :outfit")
    suspend fun updateOutfit(outfit: String?)

    @Query("UPDATE corgi_data SET unlockedOutfits = :outfits")
    suspend fun updateUnlockedOutfits(outfits: String)

    @Query("UPDATE corgi_data SET lastActiveDate = :date")
    suspend fun updateLastActiveDate(date: String)

    @Query("UPDATE corgi_data SET totalCompleted = totalCompleted + 1")
    suspend fun incrementTotalCompleted()

    @Query("UPDATE corgi_data SET consecutiveDays = consecutiveDays + 1")
    suspend fun incrementConsecutiveDays()

    @Query("UPDATE corgi_data SET consecutiveDays = 0")
    suspend fun resetConsecutiveDays()

    @Query("UPDATE corgi_data SET unlockedAchievements = :achievements")
    suspend fun updateUnlockedAchievements(achievements: String)

    @Query("UPDATE corgi_data SET maxConsecutiveDays = :days")
    suspend fun updateMaxConsecutiveDays(days: Int)

    @Query("UPDATE corgi_data SET name = :name WHERE id = (SELECT id FROM corgi_data LIMIT 1)")
    suspend fun updateName(name: String)

    /**
     * 更新用户头像路径
     *
     * @param path 头像文件绝对路径 / content URI；传 null 表示清除（恢复首字母占位）
     */
    @Query("UPDATE corgi_data SET avatarPath = :path WHERE id = (SELECT id FROM corgi_data LIMIT 1)")
    suspend fun updateAvatarPath(path: String?)

    /**
     * 更新性别字段
     *
     * @param gender 性别值（"MALE" / "FEMALE" / "OTHER"）；传 null 表示未设置
     */
    @Query("UPDATE corgi_data SET gender = :gender WHERE id = (SELECT id FROM corgi_data LIMIT 1)")
    suspend fun updateGender(gender: String?)

    /**
     * 更新用户签名
     *
     * @param signature 签名文本
     */
    @Query("UPDATE corgi_data SET signature = :signature WHERE id = (SELECT id FROM corgi_data LIMIT 1)")
    suspend fun updateSignature(signature: String)
}
