package com.msi.data.db

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SQLitePagingSource<T : Any>(
    private val queryBlock: (limit: Int, offset: Int) -> List<T>
) : PagingSource<Int, T>() {
    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        return try {
            val position = params.key ?: 0
            val limit = params.loadSize
            val data = withContext(Dispatchers.IO) {
                queryBlock(limit, position)
            }
            LoadResult.Page(
                data = data,
                prevKey = if (position == 0) null else maxOf(0, position - limit),
                nextKey = if (data.isEmpty()) null else position + limit
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
