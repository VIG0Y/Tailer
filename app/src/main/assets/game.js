'use strict';
const Puzzle=(()=>{
  function slide(board,dir){
    const next=Array(16).fill(0);let gain=0;
    for(let line=0;line<4;line++){
      const indexes=Array.from({length:4},(_,k)=>dir===0?line*4+k:dir===1?k*4+line:dir===2?line*4+3-k:(3-k)*4+line);
      const values=indexes.map(i=>board[i]).filter(Boolean), packed=[];
      for(let i=0;i<values.length;i++){
        let v=values[i];if(v===values[i+1]){v*=2;gain+=v;i++;}packed.push(v);
      }
      packed.forEach((v,i)=>next[indexes[i]]=v);
    }
    return {board:next,gain,changed:next.some((v,i)=>v!==board[i])};
  }
  function empties(board){const result=[];board.forEach((v,i)=>{if(!v)result.push(i);});return result;}
  function spawn(board){const spaces=empties(board);if(spaces.length)board[spaces[Math.floor(Math.random()*spaces.length)]]=Math.random()<.9?2:4;}
  function evaluate(board){
    const weights=[15,14,13,12,8,9,10,11,7,6,5,4,0,1,2,3];
    let snake=0,rough=0;
    board.forEach((v,i)=>{
      if(!v)return;snake+=v*weights[i]*weights[i]*.032;
      if(i%4<3&&board[i+1])rough+=Math.abs(Math.log2(v)-Math.log2(board[i+1]));
      if(i<12&&board[i+4])rough+=Math.abs(Math.log2(v)-Math.log2(board[i+4]));
    });
    return snake+empties(board).length*95-rough*5;
  }
  function valueAfterSpawn(board,depth){
    const spaces=empties(board);
    if(depth===0||!spaces.length)return evaluate(board);
    let sum=0;
    for(const i of spaces)for(const [tile,weight] of [[2,.9],[4,.1]]){
      const variation=board.slice();variation[i]=tile;
      let best=-Infinity;
      for(let d=0;d<4;d++){const m=slide(variation,d);if(m.changed)best=Math.max(best,m.gain+valueAfterSpawn(m.board,depth-1));}
      sum+=weight*(Number.isFinite(best)?best:evaluate(variation)-1500);
    }
    return sum/spaces.length;
  }
  function choose(board){
    let best=-Infinity,dir=-1;
    for(let d=0;d<4;d++){const m=slide(board,d);if(!m.changed)continue;const value=m.gain+valueAfterSpawn(m.board,1);if(value>best){best=value;dir=d;}}
    return dir;
  }
  function fresh(){const board=Array(16).fill(0);spawn(board);spawn(board);return {board,score:0};}
  return {slide,spawn,choose,fresh};
})();
