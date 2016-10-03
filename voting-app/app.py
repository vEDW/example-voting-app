from flask import Flask
from flask import render_template
from flask import request
from flask import make_response
from utils import connect_to_redis
import os
import socket
import random
import urlparse
import redis
import json


port = int(os.getenv('PORT'))
option_a = os.getenv('OPTION_A', "Hillary")
option_b = os.getenv('OPTION_B', "Trump")
hostname = os.getenv('VCAP_APP_HOST') 
hostnameString = '( IP:' + os.getenv('CF_INSTANCE_IP') + ': Index: ' + os.getenv('CF_INSTANCE_INDEX') + ': Port: ' + os.getenv('CF_INSTANCE_PORT')+ ' )' 

rediscloud_service = json.loads(os.environ['VCAP_SERVICES'])['rediscloud'][0]
credentials = rediscloud_service['credentials']
redis = redis.Redis(host=credentials['hostname'], port=credentials['port'], password=credentials['password'])

app = Flask(__name__)


@app.route("/", methods=['POST','GET'])
def hello():
    voter_id = request.cookies.get('voter_id')
    if not voter_id:
        voter_id = hex(random.getrandbits(64))[2:-1]

    vote = None

    if request.method == 'POST':
        vote = request.form['vote']
        data = json.dumps({'voter_id': voter_id, 'vote': vote})
        redis.rpush('votes', data)

    resp = make_response(render_template(
        'index.html',
        option_a=option_a,
        option_b=option_b,
        hostnameString=hostnameString,
        vote=vote,
    ))
    resp.set_cookie('voter_id', voter_id)
    return resp


if __name__ == "__main__":
    app.run(host='0.0.0.0', port=port, debug=True)
