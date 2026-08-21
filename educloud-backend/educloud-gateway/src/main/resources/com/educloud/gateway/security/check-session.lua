local values = redis.call('HMGET', KEYS[1], 'subject', 'status', 'tokenVersion')
local ttl = redis.call('PTTL', KEYS[1])
if ttl == -2 then
  return {0}
end
return {1, values[1] or '', values[2] or '', values[3] or '', ttl}
