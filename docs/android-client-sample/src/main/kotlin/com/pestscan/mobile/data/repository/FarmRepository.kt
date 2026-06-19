package com.pestscan.mobile.data.repository

import com.pestscan.mobile.data.local.FarmDao
import com.pestscan.mobile.data.local.FarmEntity
import com.pestscan.mobile.data.remote.PestScoutApi
import com.pestscan.mobile.domain.model.Farm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FarmRepository(
    private val api: PestScoutApi,
    private val farmDao: FarmDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val farms: Flow<List<Farm>> = farmDao.observeFarms().map { entities ->
        entities.map { entity -> entity.toDomain() }
    }

    suspend fun refreshFarms() = withContext(ioDispatcher) {
        val remote = api.getFarms()
        val entities = remote.map { dto ->
            FarmEntity(
                id = dto.id,
                name = dto.name,
                licensedArea = dto.licensedArea,
                updatedAt = dto.updatedAt
            )
        }
        farmDao.upsertAll(entities)
    }

    private fun FarmEntity.toDomain(): Farm = Farm(
        id = id,
        name = name,
        licensedArea = licensedArea,
        updatedAt = updatedAt
    )
}
