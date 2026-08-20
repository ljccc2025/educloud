local time = redis.call('TIME')
local redisNowMillis = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local owner = ARGV[1]
local ttlMillis = ARGV[2]

for worker = 0, 31 do
    local leaseKey = KEYS[worker + 1]
    local watermarkKey = KEYS[worker + 33]
    if not redis.call('GET', leaseKey) then
        local watermark = tonumber(redis.call('GET', watermarkKey) or '0')
        if redisNowMillis > watermark then
            local acquired = redis.call('SET', leaseKey, owner, 'NX', 'PX', ttlMillis)
            if acquired then
                return {worker, redisNowMillis}
            end
        end
    end
end

return {-1, redisNowMillis}
