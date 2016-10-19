from flask import Flask
from flask import render_template
from flask import request
from flask import make_response,request
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


vcap = json.loads(os.environ['VCAP_SERVICES'])

if 'rediscloud' in vcap:
    rediscloud_service = json.loads(os.environ['VCAP_SERVICES'])['rediscloud'][0]
elif 'p-redis' in vcap:
    rediscloud_service = json.loads(os.environ['VCAP_SERVICES'])['p-redis'][0]

#if json.loads(os.environ['VCAP_SERVICES'])['rediscloud'][0] is not 'null'
#    rediscloud_service = json.loads(os.environ['VCAP_SERVICES'])['rediscloud'][0]
#if json.loads(os.environ['VCAP_SERVICES'])['p-redis'][0] is not 'null'
#    rediscloud_service = json.loads(os.environ['VCAP_SERVICES'])['p-redis'][0]

credentials = rediscloud_service['credentials']

hostname = credentials['hostname'] if 'hostname' in credentials else credentials['host']
redis = redis.Redis(host=hostname, port=credentials['port'], password=credentials['password'])
        

app = Flask(__name__)

def shutdown_server():
    func = request.environ.get('werkzeug.server.shutdown')
    if func is None:
        raise RuntimeError('Not running with the Werkzeug Server')
    func()


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


@app.route('/shutdown', methods=['POST','GET'])
def shutdown():
    shutdown_server()
    return 'Server shutting down...'

if __name__ == "__main__":
    app.run(host='0.0.0.0', port=port, debug=True)
