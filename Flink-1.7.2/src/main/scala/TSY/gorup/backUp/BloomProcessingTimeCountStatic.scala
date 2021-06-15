//package TSY.gorup.backUp
//
//import org.apache.flink.api.common.state.{MapState, MapStateDescriptor, ValueState, ValueStateDescriptor}
//import org.apache.flink.configuration.Configuration
//import org.apache.flink.shaded.guava18.com.google.common.hash.{BloomFilter, Funnels}
//import org.apache.flink.streaming.api.TimeCharacteristic
//import org.apache.flink.streaming.api.functions.KeyedProcessFunction
//import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
//import org.apache.flink.util.Collector
//import org.slf4j.LoggerFactory
//
//import scala.collection.mutable.ArrayBuffer
//
///**
//  * \* Created with IntelliJ IDEA.
//  * \* User: aresyhzhang
//  * \* Date: 2020/10/21
//  * \* Time: 9:31
//  * 通过布隆过滤器指定key，进行去重统计
//  * 游戏id会作为keyby的条件，keyby之后取到的状态都是该key的
//  * 不同的key拿到的状态不同
//  * 不采用valuestate存储布隆过滤器的原因是key过多内存不够用
//  * \*/
//object BloomProcessingTimeCountStatic{
//  def main(args: Array[String]): Unit = {
//
//    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
//    env.setStreamTimeCharacteristic(TimeCharacteristic.ProcessingTime)
//
//
//    val gameIdPos="4".toInt-1
//    //联合主键位置
//    val mainKeyPos="5|6".split('|').map(_.toInt-1)
//    //统计字段位置
//    val staticFieldArr="7|8|9".split('|').map(_.toInt-1)
//
//    //统计结果时长(秒)
//    val timeValid="9999".toLong
//
//    val hotGameKeys=10000000
//    val otherGameKeys=1000000
//    val fpp=1E-4
//
//
//    val LOG = LoggerFactory.getLogger(this.getClass)
//
//    val input_DStream1: DataStream[String] = env.socketTextStream("9.134.217.5",9999)
//
//    def tomorrowZeroTimestampMs(now: Long, timeZone: Int): Long = now - (now + timeZone * 3600000) % 86400000 + 86400000
//
//
//    def fieldIsNull(inputStr:String): Boolean ={
//      null==inputStr || inputStr=="null" || inputStr==""
//    }
//
//    class CountStaticKeyedProcessFunction extends KeyedProcessFunction[String,String,String] {
//      private var cacheMapState:MapState[String,BloomFilter[String]]=_
//      private var countMapState:MapState[String,Long]=_
//      private var timeState: ValueState[Long] = _
//
//      override def open(parameters: Configuration): Unit = {
//        cacheMapState=getRuntimeContext.getMapState(
//          new MapStateDescriptor[String,BloomFilter[String]]
//          ("cacheMapState",classOf[String],classOf[BloomFilter[String]])
//        )
//        countMapState=getRuntimeContext.getMapState(
//          new MapStateDescriptor[String,Long]
//          ("countMapState",classOf[String],classOf[Long])
//        )
//        timeState = getRuntimeContext.getState(
//          new ValueStateDescriptor[Long]("timeState", classOf[Long])
//        )
//      }
//
//      override def processElement(input: String,
//                                  ctx: KeyedProcessFunction[String, String, String]#Context,
//                                  out: Collector[String]): Unit = {
//        val inputArr: Array[String] = input.split('|')
//
//        //注册定时器
//        if (timeState.value() == 0L){
//          var timer=0L
//          val ts=ctx.timerService().currentProcessingTime()
//          if (timeValid == 9999L) {
//            val tomorrowTS = tomorrowZeroTimestampMs(ts, 8)
//            ctx.timerService().registerProcessingTimeTimer(tomorrowTS)
//            timer = tomorrowTS
//          }
//          else {
//            timer = ts + (timeValid * 1000)
//            ctx.timerService().registerProcessingTimeTimer(timer)
//          }
//          timeState.update(timer)
//        }
//
//          var mainKey=""
//        for (ele <- mainKeyPos) {
//          mainKey +=inputArr(ele)+"#"
//        }
//
//        val resultArr: ArrayBuffer[Long] = ArrayBuffer[Long]()
//        try {
//          for (fieldIndex <- staticFieldArr) {
//            //23#fea_deltax_abnoml_low_3#0#3,需要拿到统计字段对应的布隆过滤器
//            val key=ctx.getCurrentKey+"#"+mainKey+fieldIndex
//            var countBloom= cacheMapState.get(key)
//
//            var counts=countMapState.get(key)
//
//            val field=inputArr(fieldIndex)
//
//            //更新状态和布隆
//            if (!fieldIsNull(field)){
//            if (countBloom == null) {
//                if(gameIdPos==(-1)){
//                  countBloom = BloomFilter.create(Funnels.unencodedCharsFunnel(), otherGameKeys, fpp)
//                }
//                else{
//                  //根据不同游戏创建不同大小布隆过滤器
//                  if(List("23","3","5","2577","2131").indexOf(inputArr(gameIdPos))!=(-1)){
//                    countBloom = BloomFilter.create(Funnels.unencodedCharsFunnel(), hotGameKeys, fpp)
//                  }
//                  else{
//                    countBloom = BloomFilter.create(Funnels.unencodedCharsFunnel(), otherGameKeys, fpp)
//                  }
//                }
//            }
//            if (!countBloom.mightContain(field)){
//              counts+=1
//              countMapState.put(key,counts)
//              countBloom.put(field)
//              cacheMapState.put(key,countBloom)
//            }
//
//            }
//
//            resultArr.append(counts)
//
//          }
//
//          out.collect(input+"|"+resultArr.mkString("|"))
//
//        }catch {
//          case exception: Exception=>{
//            LOG.error(input+"出错了"+exception.getMessage)
//            throw exception
//          }
//        }
//      }
//
//
//      override def onTimer(timestamp: Long,
//                           ctx: KeyedProcessFunction[String, String, String]#OnTimerContext,
//                           out: Collector[String]): Unit = {
//        LOG.info(s"""定时触发了，触发的key是${ctx.getCurrentKey},触发的时间是${timestamp}""")
//        print(s"""定时触发了，触发的key是${ctx.getCurrentKey},触发的时间是${timestamp}""")
//        timeState.clear()
//        countMapState.clear()
//        cacheMapState.clear()
//      }
//    }
//
//    val resultDS: DataStream[String] = input_DStream1
//      .keyBy(_.split('|')(gameIdPos))
//      .process(new CountStaticKeyedProcessFunction)
//      .uid("processing_bloom_count_static")
//
//    resultDS.print()
//    env.execute()
//  }
//
//
//}