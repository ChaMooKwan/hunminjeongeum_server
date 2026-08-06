package kr.ac.sunmoon.hunminjeongeum_server.data.supabase

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider{
    val client = createSupabaseClient(
        "https://ccsggafxntcgtkzxafei.supabase.co",
        "sb_publishable_GmZgLygaSw_ZUj0Lg-w0-A_cmR4cfnL"
    ){
        install(Postgrest)
    }
}
