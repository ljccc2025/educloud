if redis.call('GET', KEYS[1]) ~= ARGV[1] then
    return 0
end

local lastIssuedTimestamp = tonumber(ARGV[2])
local watermark = tonumber(redis.call('GET', KEYS[2]) or '0')
if lastIssuedTimestamp > watermark then
    redis.call('SET', KEYS[2], lastIssuedTimestamp)
end
redis.call('DEL', KEYS[1])
return 1
