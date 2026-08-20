local time = redis.call('TIME')
local redisNowMillis = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

if redis.call('GET', KEYS[1]) ~= ARGV[1] then
    return {0, redisNowMillis}
end

local lastIssuedTimestamp = tonumber(ARGV[3])
local watermark = tonumber(redis.call('GET', KEYS[2]) or '0')
if lastIssuedTimestamp > watermark then
    redis.call('SET', KEYS[2], lastIssuedTimestamp)
end
redis.call('PEXPIRE', KEYS[1], ARGV[2])
return {1, redisNowMillis}
